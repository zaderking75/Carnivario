package duocuc.cl.rodrigo.carniverocrud.controller.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class AzureJwtConfig {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new AzureAuthenticationConverter();
    }

    public static class AzureAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {

        private final JwtGrantedAuthoritiesConverter scopeConverter;

        public AzureAuthenticationConverter() {
            this.scopeConverter = new JwtGrantedAuthoritiesConverter();
            this.scopeConverter.setAuthoritiesClaimName("scp");
            this.scopeConverter.setAuthorityPrefix("SCOPE_");
        }

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {

            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Obtener permisos desde el claim scp
            Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);

            if (scopes != null) {
                authorities.addAll(scopes);
            }

            // Obtener roles desde el claim roles
            List<String> roles = jwt.getClaimAsStringList("roles");

            if (roles != null) {
                roles.forEach(role ->
                    authorities.add(
                        new SimpleGrantedAuthority("ROLE_" + role)
                    )
                );
            }

            // Obtener email del usuario
            String email = jwt.getClaimAsString("preferred_username");

            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("email");
            }

            if (email == null || email.isBlank()) {
                throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token"),
                    "El token Microsoft no contiene un email de usuario"
                );
            }

            return new JwtAuthenticationToken(jwt, authorities, email);
        }
    }
}
