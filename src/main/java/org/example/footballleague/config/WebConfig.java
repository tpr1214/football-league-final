package org.example.footballleague.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Serves uploaded files (e.g. profile images) from the on-disk uploads folder
 * under the public {@code /uploads/**} path so the frontend can load them
 * directly via an <img> tag.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        String location = "file:" + absolutePath.replace("\\", "/") + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
