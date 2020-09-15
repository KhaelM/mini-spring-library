/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author miker
 */
public class Mapper {

    public static Object mapRequestToObject(HttpServletRequest request, Class objectClass) throws InstantiationException, IllegalAccessException, ParseException {
        Enumeration<String> paramNames = request.getParameterNames();
        Object instance = objectClass.newInstance();
        Field field;
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            try {
                field = objectClass.getDeclaredField(paramName);
            } catch(NoSuchFieldException e) {
                continue;
            }
            System.out.println(field.getType());
            field.setAccessible(true);
            if (!request.getParameter(paramName).isEmpty()) {
                if (field.getType().equals(Date.class)) {
                    String pattern = "yyyy-MM-dd";
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
                    field.set(instance, simpleDateFormat.parse(request.getParameter(paramName)));
                } else if (field.getType().equals(String.class)) {
                    field.set(instance, request.getParameter(paramName));
                } else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)) {
                    field.set(instance, Integer.valueOf(request.getParameter(paramName)));
                }
            }
        }
        return instance;
    }
}
