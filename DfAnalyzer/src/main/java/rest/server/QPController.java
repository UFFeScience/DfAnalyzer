package rest.server;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import qp.query.QueryValidationException;
import rest.config.QPHandler;

/**
 *
 * @author vitor, débora
 */
@RestController
@RequestMapping("/query_interface")
public class QPController {

    private static final String MESSAGE_PREFIX = "message=";

    @Autowired
    private QPHandler qpHandler;

    @PostMapping(value = "/{df_tag}/{df_id}")
    public ResponseEntity<String> dataflow(@PathVariable("df_tag") String dataflowTag,
            @PathVariable("df_id") Integer dataflowID,
            @RequestBody String message) throws UnsupportedEncodingException {

        String specification = extractSpecification(message);
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(qpHandler.runQuery(dataflowTag, dataflowID, specification));
        } catch (QueryValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        }
    }

    /** O cliente antigo envia um formulário com um único campo, message. */
    private static String extractSpecification(String body) throws UnsupportedEncodingException {
        String decoded = URLDecoder.decode(body, "UTF-8");
        return decoded.startsWith(MESSAGE_PREFIX)
                ? decoded.substring(MESSAGE_PREFIX.length())
                : decoded;
    }
}
