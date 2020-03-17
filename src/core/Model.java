/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.HashMap;

/**
 *
 * @author miker
 */
public class Model {
    HashMap<String, Object> attributes = new HashMap<String,Object>();
    
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public HashMap<String, Object> getAttributes() {
        return attributes;
    }
}
