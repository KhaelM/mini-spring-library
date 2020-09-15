/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author miker
 */
public class Model {
    HashMap<String, Object> attributes = new HashMap<String,Object>();
    HashMap<String, Object> sessionAttributes = new HashMap<String, Object>();
    List<String> sessionAttributesToRemove = new ArrayList<String>();
    HttpServletRequest request;
    
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }
    
    public void addToSession(String key, Object value) {
        sessionAttributes.put(key, value);
    }

    public HashMap<String, Object> getSessionAttributes() {
        return sessionAttributes;
    }
    
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public HashMap<String, Object> getAttributes() {
        return attributes;
    }
    
    public Object getSessionAttribute(String name) {
        return sessionAttributes.get(name);
    }
    
    public void removeFromSession(String name) {
        sessionAttributesToRemove.add(name);
    }

    public List<String> getSessionAttributesToRemove() {
        return sessionAttributesToRemove;
    }
    
    public String getRequestParameter(String name) {
        return request.getParameter(name);
    }
}
