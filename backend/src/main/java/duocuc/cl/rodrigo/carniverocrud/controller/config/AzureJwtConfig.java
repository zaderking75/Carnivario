package duocuc.cl.rodrigo.carniverocrud.controller.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class AzureJwtConfig {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter scopeConverter =
                new JwtGrantedAuthoritiesConverter();

        scopeConverter.setAuthoritiesClaimName("scp");
        scopeConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setPrincipalClaimName("preferred_username");

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities =
                    new ArrayList<>();

            Collection<GrantedAuthority> scopes =
                    scopeConverter.convert(jwt);

            if (scopes != null) {
                authorities.addAll(scopes);
            }

            List<String> roles =
                    jwt.getClaimAsStringList("roles");

            if (roles != null) {
                roles.forEach(role ->
                        authorities.add(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role
                                )
                        )
                );
            }

            return authorities;
        });

        return jwt -> {
            String email = jwt.getClaimAsString("preferred_username");
            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("email");
            }
            if (email == null || email.isBlank()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
                        "El token Microsoft no contiene un email de usuario");
            }
            return new JwtAuthenticationToken(jwt, converter.convert(jwt).getAuthorities(), email);
        };
    }
}
