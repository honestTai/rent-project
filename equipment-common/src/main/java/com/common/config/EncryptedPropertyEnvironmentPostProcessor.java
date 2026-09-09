package com.common.config;

import com.common.Encryption.Aes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();
        List<PropertySource<?>> snapshot = new ArrayList<>();
        propertySources.forEach(snapshot::add);

        for (PropertySource<?> propertySource : snapshot) {
            PropertySource<?> wrapped = wrap(propertySource);
            if (wrapped != propertySource) {
                propertySources.replace(propertySource.getName(), wrapped);
            }
        }
    }

    private PropertySource<?> wrap(PropertySource<?> propertySource) {
        // Skip Spring Boot's configuration property sources
        if (propertySource.getClass().getName().equals("org.springframework.boot.context.properties.source.ConfigurationPropertySourcesPropertySource")) {
            return propertySource;
        }
        if (propertySource instanceof EncryptablePropertySource
                || propertySource instanceof EncryptableEnumerablePropertySource) {
            return propertySource;
        }
        if (propertySource instanceof EnumerablePropertySource) {
            return new EncryptableEnumerablePropertySource((EnumerablePropertySource<?>) propertySource);
        }
        return new EncryptablePropertySource(propertySource);
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private static Object decryptIfNecessary(String propertyName, String sourceName, Object value) {
        if (!(value instanceof String)) {
            return value;
        }

        String text = (String) value;
        if (!text.startsWith(ENC_PREFIX) || !text.endsWith(ENC_SUFFIX)) {
            return value;
        }

        String cipherText = text.substring(ENC_PREFIX.length(), text.length() - ENC_SUFFIX.length());
        if (cipherText.isEmpty()) {
            return "";
        }

        try {
            return Aes.decrypt(cipherText);
        } catch (RuntimeException ex) {
            System.err.println("[WARN] Failed to decrypt property '" + propertyName
                    + "' from '" + sourceName + "': " + ex.getMessage()
                    + " — keeping original ENC(...) value");
            return value;
        }
    }

    private static final class EncryptablePropertySource extends PropertySource<PropertySource<?>> {

        private final PropertySource<?> delegate;

        private EncryptablePropertySource(PropertySource<?> delegate) {
            super(delegate.getName(), delegate);
            this.delegate = delegate;
        }

        @Override
        public Object getProperty(String name) {
            return decryptIfNecessary(name, getName(), delegate.getProperty(name));
        }
    }

    private static final class EncryptableEnumerablePropertySource
            extends EnumerablePropertySource<EnumerablePropertySource<?>> {

        private final EnumerablePropertySource<?> delegate;

        private EncryptableEnumerablePropertySource(EnumerablePropertySource<?> delegate) {
            super(delegate.getName(), delegate);
            this.delegate = delegate;
        }

        @Override
        public String[] getPropertyNames() {
            return delegate.getPropertyNames();
        }

        @Override
        public Object getProperty(String name) {
            return decryptIfNecessary(name, getName(), delegate.getProperty(name));
        }
    }
}
