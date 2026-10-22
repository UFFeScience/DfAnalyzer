package rest.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serve a aplicação do DfA Studio.
 *
 * @author joao
 */
@Controller
public class StudioController {

    @RequestMapping({
        "/studio",
        "/studio/",
        "/studio/{path:[^\\.]*}",
        "/studio/**/{path:[^\\.]*}"
    })
    public String studio() {
        return "forward:/studio/index.html";
    }
}
