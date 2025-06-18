package org.treepluginframework.WiringSystem;

import org.treepluginframework.TPFQueryMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class TPFQueryHandler {

    private static Map<Class<?>, Method> queryHandlers = new HashMap<>();

    //I don't like that this is public. I need to do the static thing again.
    public void registerQueryHandler(Class<?> handlerClass) {
        for (Method method : handlerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(TPFQueryMapping.class)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1) {
                    throw new RuntimeException("TPFQueryMapping methods must have exactly one parameter");
                }
                Class<?> queryType = params[0];
                if (queryHandlers.containsKey(queryType)) {
                    throw new RuntimeException("Duplicate handler for query type: " + queryType.getName());
                }
                queryHandlers.put(queryType, method);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <R> R query(TPFQuery<R> request) {
        Method handler = queryHandlers.get(request.getClass());
        if (handler == null) {
            throw new RuntimeException("No handler for query type " + request.getClass().getName());
        }
        try {
            Object handlerInstance = TPFContext.get(handler.getDeclaringClass()); // from your context
            return (R) handler.invoke(handlerInstance, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void init(){

    }
}
