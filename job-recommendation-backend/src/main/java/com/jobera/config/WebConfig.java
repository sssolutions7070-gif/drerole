// src/main/java/com/jobera/config/WebConfig.java
package com.jobera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOriginPatterns;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String[] allowedOriginPatterns) {
        this.authInterceptor = authInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Only profile pictures are public; resumes and credential files are served through authenticated endpoints
        Path profilesDir = Paths.get("uploads/profiles").toAbsolutePath().normalize();
        String profilesPath = "file:" + profilesDir.toString() + "/";
        
        registry.addResourceHandler("/uploads/profiles/**")
                .addResourceLocations(profilesPath)
                .setCachePeriod(3600)
                .resourceChain(true);

        // Video intros are shown to recruiters alongside a candidate's profile, same public
        // visibility as the profile picture above.
        Path videoIntrosDir = Paths.get("uploads/video-intros").toAbsolutePath().normalize();
        String videoIntrosPath = "file:" + videoIntrosDir.toString() + "/";

        registry.addResourceHandler("/uploads/video-intros/**")
                .addResourceLocations(videoIntrosPath)
                .setCachePeriod(3600)
                .resourceChain(true);

        // Company logos/banners on the public employer branding page - public the same way.
        Path companyLogosDir = Paths.get("uploads/company-logos").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/company-logos/**")
                .addResourceLocations("file:" + companyLogosDir.toString() + "/")
                .setCachePeriod(3600)
                .resourceChain(true);

        Path companyBannersDir = Paths.get("uploads/company-banners").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/company-banners/**")
                .addResourceLocations("file:" + companyBannersDir.toString() + "/")
                .setCachePeriod(3600)
                .resourceChain(true);
    }
}