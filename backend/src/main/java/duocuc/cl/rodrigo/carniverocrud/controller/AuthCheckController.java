package duocuc.cl.rodrigo.carniverocrud.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthCheckController {

    @GetMapping("/azure-check")
    public Map<String, Object> azureCheck(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tokenInfo(
                jwt,
                "Azure JWT valido"
        );
    }

    @PostMapping("/azure-admin-check")
    public Map<String, Object> azureAdminCheck(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tokenInfo(
                jwt,
                "Azure JWT ADMIN valido"
        );
    }

    private Map<String, Object> tokenInfo(
            Jwt jwt,
            String message
    ) {
        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("message", message);

        response.put(
                "iss",
                jwt.getIssuer() != null
                        ? jwt.getIssuer().toString()
                        : null
        );

        response.put("aud", jwt.getAudience());
        response.put("scp", jwt.getClaimAsString("scp"));
        response.put(
                "roles",
                jwt.getClaimAsStringList("roles")
        );
        response.put(
                "preferred_username",
                jwt.getClaimAsString("preferred_username")
        );

        return response;
    }
}
