/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package servlet;

import annotation.Controller;
import annotation.Folder;
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
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
//        response.setContentType("text/html;charset=UTF-8");
        String controllersPackageName = this.getServletConfig().getInitParameter("controllers-package");
        String path = getServletContext().getRealPath("/WEB-INF/classes/" + controllersPackageName.replace('.', '/'));
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("folder not found");
        } else {
            System.out.println("folder found :)");
        }

        String fullUrl = (String) request.getAttribute("fullUrl");
        request.removeAttribute("fullUrl");
        String[] splittedByContext = fullUrl.split(request.getContextPath());
        String urlWithoutContext = splittedByContext[1];
        Class controllerClass;
        String[] packageUnsplitted;
        String[] packageSpittedByAntiSlash;
        String fullClassName;
        String methodUrlPath;
        String controllerPath;
        String methodPath;
        Method[] controllerMethods;
        Object controllerInstance = null;
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {

            List<String> result = walk.map(x -> x.toString())
                    .filter(f -> f.endsWith(".class")).collect(Collectors.toList());

            for (String res : result) {
                packageUnsplitted = res.split(controllersPackageName);
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
                    controllerClass = Class.forName(fullClassName);
                    if (!controllerClass.isAnnotationPresent(Controller.class)) {
                        continue;
                    }
                    System.out.println("controller found: " + controllerClass.getSimpleName());
                    try {
                        controllerInstance = controllerClass.newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    controllerPath = ((Controller) controllerClass.getAnnotation(Controller.class)).path();
                    if (!(urlWithoutContext.contains(controllerPath) && urlWithoutContext.indexOf(controllerPath) == 0)) {
                        continue;
                    }
                    methodUrlPath = (urlWithoutContext.split(controllerPath)[1]).split("[.]")[0];
                    controllerMethods = controllerClass.getMethods();
                    for (Method controllerMethod : controllerMethods) {
                        if (!controllerMethod.isAnnotationPresent(annotation.Path.class)) {
                            continue;
                        }
                        methodPath = ((annotation.Path) controllerMethod.getAnnotation(annotation.Path.class)).name();
                        if (!methodUrlPath.equals(methodPath)) {
                            continue;
                        }

                        boolean hasMappingObject = false;
                        boolean hasModel = false;
                        Object mappingObject = null;
                        Class[] parametersClasses = controllerMethod.getParameterTypes();
                        try {
                            if (parametersClasses.length != 0) {
                                for (int i = 0; i < parametersClasses.length; i++) {
                                    if (!parametersClasses[i].equals(Model.class)) {
                                        mappingObject = Mapper.mapRequestToObject(request, parametersClasses[i]);
                                        hasMappingObject = true;
                                    } else {
                                        hasModel = true;
                                    }
                                }
                            }
                        } catch (InstantiationException ex) {
                            Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalAccessException ex) {
                            Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchFieldException ex) {
                            Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ParseException ex) {
                            Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        Model model = new Model();
                        if (controllerMethod.getReturnType().equals(String.class)) {
                            System.out.println("String le return");
                            try {
                                String view = "";
                                if (hasMappingObject) {
                                    if (hasModel) {
                                        view = (String) controllerMethod.invoke(controllerInstance, mappingObject, model);
                                    } else {
                                        view = (String) controllerMethod.invoke(controllerInstance, mappingObject);
                                    }
                                } else {
                                    if (hasModel) {
                                        view = (String) controllerMethod.invoke(controllerInstance, model);
                                    } else {
                                        view = (String) controllerMethod.invoke(controllerInstance);
                                    }
                                }

                                for (Map.Entry<String, Object> entry : model.getAttributes().entrySet()) {
                                    String key = entry.getKey();
                                    Object value = entry.getValue();
                                    request.setAttribute(key, value);
                                }
                                
                                HttpSession session = request.getSession();
                                for (Map.Entry<String, Object> entry : model.getSessionAttributes().entrySet()) {
                                    String key = entry.getKey();
                                    Object value = entry.getValue();
                                    session.setAttribute(key, value);
                                }

                                System.out.println("method found: " + controllerMethod.getName());
                                System.out.println(view);
                                String folder = "/WEB-INF/";
                                if (controllerMethod.isAnnotationPresent(Folder.class)) {
                                    folder += ((Folder) controllerMethod.getAnnotation(Folder.class)).path() + "/";
                                }
                                System.out.println("folder:" + folder);
                                getServletContext().getRequestDispatcher(folder + view + ".jsp").forward(request, response);
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else if (controllerMethod.getReturnType().equals(Json.class)) {
                            PrintWriter out = response.getWriter();
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            Json json = null;
                            if(hasMappingObject) {
                                try {
                                    json = (Json) controllerMethod.invoke(controllerInstance, mappingObject);
                                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                    Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                try {
                                    json = (Json) controllerMethod.invoke(controllerInstance);
                                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                    Logger.getLogger(MiniSpring.class.getName()).log(Level.SEVERE, null, ex);
                                }
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

// <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
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
