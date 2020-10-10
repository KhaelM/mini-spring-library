/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package servlet;

import annotation.Controller;
import annotation.Folder;
import annotation.PathVariable;
import core.Model;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import core.Json;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.http.HttpSession;
import utils.Mapper;

/**
 *
 * @author miker
 */
public class MiniSpring extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // response.setContentType("text/html;charset=UTF-8");
        
        // On cherche le dossier contenant les controlleurs
        // On lit le fichier web.xml
        String controllersPackageName = this.getServletConfig().getInitParameter("controllers-package");
        String path = getServletContext().getRealPath("/WEB-INF/classes/" + controllersPackageName.replace('.', '/'));
        System.out.println("path = " + path);
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("Le dossier contenant les controlleurs n'a pas été trouvé.");
        }

        String fullUrl = (String) request.getAttribute("fullUrl");
        request.removeAttribute("fullUrl");
        String[] splittedByContext = fullUrl.split(request.getContextPath());
        System.out.println("fullUrl = " + fullUrl);
        String urlWithoutContext = splittedByContext[1];
        System.out.println("urlWithoutContext = " + urlWithoutContext);
        Class controllerClass;
        String[] packageUnsplitted;
        String[] packageSpittedByAntiSlash;
        String fullClassName;
        String methodUrlPath;
        String controllerPath;
        String methodPath;
        Method[] controllerMethods;
        Object controllerInstance = null;
        
        // Charge tous les fichiers qui sont dans le dossier du controlleur
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {

            // Garder uniquement les fichier de type class
            List<String> result = walk.map(x -> x.toString()).filter(f -> f.endsWith(".class"))
                    .collect(Collectors.toList());

            for (String res : result) {
                // res est quelque chose du style: /WEB-INF/classes/{controllersPackageName}/NomClasse.class
                packageUnsplitted = res.split(controllersPackageName);
                // Sous Windows '\\\\' et sous Linux ou MacOS '/'.
                packageSpittedByAntiSlash = packageUnsplitted[1].split("\\\\");
                fullClassName = controllersPackageName + ".";
                for (int i = 1; i < packageSpittedByAntiSlash.length; i++) {
                    if (i == packageSpittedByAntiSlash.length - 1) {
                        fullClassName += packageSpittedByAntiSlash[i].split(".class")[0];
                    } else {
                        fullClassName += packageSpittedByAntiSlash[i] + ".";
                    }
                }

                try {
                    System.out.println("fullClassName = " + fullClassName);
                    controllerClass = Class.forName(fullClassName);
                    if (!controllerClass.isAnnotationPresent(Controller.class)) {
                        continue;
                    }
                    // System.out.println("controller found: " + controllerClass.getSimpleName());
                    try {
                        controllerInstance = controllerClass.newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException("Erreur lors de l'instanciantion du controlleur " + controllerClass);
                    }
                    controllerPath = ((Controller) controllerClass.getAnnotation(Controller.class)).path();
                    System.out.println("controllerPath = " + controllerPath);
                    if (!(urlWithoutContext.contains(controllerPath)
                            && urlWithoutContext.indexOf(controllerPath) == 0)) {
                        continue;
                    }

                    methodUrlPath = (urlWithoutContext.split(controllerPath)[1]).split("[.]")[0];
                    System.out.println("methodUrlPath = " + methodUrlPath);

                    controllerMethods = controllerClass.getMethods();
                    for (Method controllerMethod : controllerMethods) {
                        if (!controllerMethod.isAnnotationPresent(annotation.Path.class)) {
                            continue;
                        }
                        methodPath = ((annotation.Path) controllerMethod.getAnnotation(annotation.Path.class)).name();
                        String[] urlMethPathSplitted = methodUrlPath.split("/");
                        String[] methPathSplitted = methodPath.split("/");

                        if (urlMethPathSplitted.length != methPathSplitted.length) {
                            continue;
                        }

                        // Check http verbs
                        // On vérifie si le verbe HTTP de la requete actuelle
                        // est contenu dans la liste des verbes HTTP de l'actuelle méthode
                        String method = request.getMethod();
                        List<String> methodsSupported = new ArrayList<String>(Arrays.asList(
                                ((annotation.Path) controllerMethod.getAnnotation(annotation.Path.class)).httpVerbs()));
                        if (!methodsSupported.contains(method)) {
                            continue;
                        }

                        boolean correctController = true;
                        Map<String, String> paramNamesValues = new HashMap<String, String>();
                        for (int i = 0; i < methPathSplitted.length; i++) {
                            if (methPathSplitted[i].isEmpty() && urlMethPathSplitted[i].isEmpty()) {
                                continue;
                            }
                            if (methPathSplitted[i].charAt(0) != '{'
                                    && !methPathSplitted[i].equals(urlMethPathSplitted[i])) {
                                correctController = false;
                                break;
                            } else {
                                // Removing { and }
                                paramNamesValues.put(methPathSplitted[i].substring(1, methPathSplitted[i].length() - 1),
                                        urlMethPathSplitted[i]);
                            }
                        }

                        if (!correctController) {
                            continue;
                        }

                        final Annotation[][] paramAnnotations = controllerMethod.getParameterAnnotations();
                        final Class<?>[] paramTypes = controllerMethod.getParameterTypes();
                        List<Object> arguments = new ArrayList<Object>();

                        // Copy session attributes to Model to can read them inside controller method
                        Model model = new Model();
                        Enumeration<String> sessionAttributes = request.getSession().getAttributeNames();
                        while (sessionAttributes.hasMoreElements()) {
                            String attribute = (String) sessionAttributes.nextElement();
                            model.addToSession(attribute, request.getSession().getAttribute(attribute));
                        }

                        // Set RequestParam
                        model.setRequest(request);
                        System.out.println(controllerMethod);
                        try {
                            for (int i = 0; i < paramTypes.length; i++) {
                                // Si le type actuel ne possède pas d'annotations
                                if (paramAnnotations[i].length == 0) {
                                    if (paramTypes[i].equals(Model.class)) {
                                        arguments.add(model);
                                    } else {
                                        arguments.add(Mapper.mapRequestToObject(request, paramTypes[0]));
                                    }
                                } else {
                                    if (paramAnnotations[i][0] instanceof PathVariable) {
                                        String paramName = ((PathVariable) paramAnnotations[i][0]).name();
                                        if (paramTypes[i].equals(int.class) || paramTypes[i].equals(Integer.class)) {
                                            arguments.add(Integer.parseInt(paramNamesValues.get(paramName)));
                                        } else if (paramTypes[i].equals(String.class)) {
                                            arguments.add(paramNamesValues.get(paramName));
                                        } else if (paramTypes[i].equals(float.class)
                                                || paramTypes[i].equals(Float.class)) {
                                            arguments.add(Float.parseFloat(paramNamesValues.get(paramName)));
                                        } else if (paramTypes[i].equals(double.class)
                                                || paramTypes[i].equals(Double.class)) {
                                            arguments.add(Double.parseDouble(paramNamesValues.get(paramName)));
                                        }
                                    }
                                }
                            }
                        } catch (InstantiationException | IllegalAccessException | ParseException ex) {
                            Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NumberFormatException ex) {
                            System.out.println("NumberFormatException inside method Controller " + controllerMethod);
                            continue;
                        }

                        if (controllerMethod.getReturnType().equals(String.class)) {
                            try {
                                String view = (String) controllerMethod.invoke(controllerInstance, arguments.toArray());

                                // Copie les attributs du modèle indiqués par le programmeur dans le scope request
                                for (Map.Entry<String, Object> entry : model.getAttributes().entrySet()) {
                                    String key = entry.getKey();
                                    Object value = entry.getValue();
                                    request.setAttribute(key, value);
                                }

                                // Copie les attributs du modèle indiqués par le programmeur dans le scope session
                                HttpSession session = request.getSession();
                                for (Map.Entry<String, Object> entry : model.getSessionAttributes().entrySet()) {
                                    String key = entry.getKey();
                                    Object value = entry.getValue();
                                    session.setAttribute(key, value);
                                }

                                // Delete attributes in session
                                for (String attr : model.getSessionAttributesToRemove()) {
                                    session.removeAttribute(attr);
                                }

                                // System.out.println("method found: " + controllerMethod.getName());
                                // System.out.println(view);
                                String folder = "/WEB-INF/";
                                if (controllerMethod.isAnnotationPresent(Folder.class)) {
                                    folder += ((Folder) controllerMethod.getAnnotation(Folder.class)).path() + "/";
                                }
                                // System.out.println("folder:" + folder);
                                getServletContext().getRequestDispatcher(folder + view + ".jsp").forward(request,
                                        response);
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else if (controllerMethod.getReturnType().equals(Json.class)) {
                            PrintWriter out = response.getWriter();
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            Json json = null;
                            try {
                                json = (Json) controllerMethod.invoke(controllerInstance, arguments.toArray());
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            out.print(json.printJson());
                            out.flush();
                        }
                    }
                } catch (ClassNotFoundException ex) {
                    System.out.println("class" + fullClassName + " tsy hita");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the
    // + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
