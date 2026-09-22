package com.jobera.security;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;

import jakarta.servlet.http.HttpServletRequest;

/** Rejects JSON bodies that reference someone else's recruiterId/userId or resources the caller does not own. */
@ControllerAdvice
public class BodyOwnershipAdvice extends RequestBodyAdviceAdapter {

    private final AccessPolicy accessPolicy;

    public BodyOwnershipAdvice(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!(body instanceof Map<?, ?> map)) {
            return body;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return body;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        if (!(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER) instanceof User user)) {
            return body;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        if (!accessPolicy.isBodyAllowed(user, request.getMethod(), request.getRequestURI(),
                AuthInterceptor.queryParams(request), typed)) {
            throw new ForbiddenException("You do not have permission to use the resources referenced in this request");
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
