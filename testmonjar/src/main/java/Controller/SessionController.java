package Controller;
import com.lovapinto.MyController;
import com.lovapinto.MySession;
import com.lovapinto.Autowired;
import com.lovapinto.Model;
import com.lovapinto.ModelAndView;
import com.lovapinto.MyRequestParam;
import com.lovapinto.UrlMapping;
import com.lovapinto.WebApi;
import Dto.User;
import Repository.UserRepository;

import java.util.Map;
import java.util.LinkedHashMap;

@MyController
public class SessionController {

    @WebApi 
    @UrlMapping(path = "session/creer")
    public Map<String, Object> creer(MySession session) {
        session.setAttribute("nom", "Lova");
        session.setAttribute("idUtilisateur", 10);
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("message", "Session créée et données enregistrées");
        resultat.put("sessionId", session.getId());
        resultat.put("nom", session.getAttribute("nom"));
        return resultat;
    }

    @UrlMapping(path="session/lire")
    public Map<String, Object> lire(MySession session) {

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("sessionId", session.getId());
        resultat.put("nom", session.getAttribute("nom"));
        resultat.put("idUtilisateur",
                session.getAttribute("idUtilisateur"));

        return resultat;
    }

    @UrlMapping(path="/session/detruire")
    public String detruire(MySession session) {
        session.invalidate();
        return "Session détruite";
    }
}
