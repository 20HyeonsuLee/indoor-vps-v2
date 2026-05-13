package kr.ac.koreatech.indoor.vps.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@Hidden
public class DocsAliasController {
    @GetMapping("/v3/api-docs")
    public RedirectView springdocJsonAlias() {
        return new RedirectView("/openapi.json");
    }

    @GetMapping({"/swagger-ui", "/swagger-ui/", "/swagger-ui/index.html"})
    public RedirectView swaggerUiAlias() {
        return new RedirectView("/docs");
    }
}
