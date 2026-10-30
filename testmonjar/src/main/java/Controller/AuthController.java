package Controller;

import com.lovapinto.Auth;
import com.lovapinto.Model;
import com.lovapinto.ModelAndView;
import com.lovapinto.MyController;
import com.lovapinto.MyRequestParam;
import com.lovapinto.MySession;
import com.lovapinto.UrlMapping;
import com.lovapinto.WebApi;

import java.util.LinkedHashMap;
import java.util.Map;

@MyController
public class AuthController {

    @UrlMapping(path = "/login")
    public ModelAndView loginForm() {
        return new ModelAndView("auth/login");
    }

    @UrlMapping(path = "/login", method = "POST")
    public ModelAndView login(
            @MyRequestParam("username") String username,
            @MyRequestParam("password") String password,
            MySession session) {
        if ("admin".equals(username) && "1234".equals(password)) {
            session.setAttribute("user", username);
            session.setAttribute("role", "ADMIN");

            Model model = new Model();
            model.setAttribute("user", username);
            return new ModelAndView("auth/welcome").setModelContainer(model);
        }

        Model model = new Model();
        model.setAttribute("error", "Identifiants invalides");
        return new ModelAndView("auth/login").setModelContainer(model);
    }

    @UrlMapping(path = "/logout")
    public String logout(MySession session) {
        session.invalidate();
        return "auth/login";
    }

    @Auth
    @UrlMapping(path = "/secure")
    public ModelAndView secure(MySession session) {
        Model model = new Model();
        model.setAttribute("user", session.getAttribute("user"));
        model.setAttribute("role", session.getAttribute("role"));
        return new ModelAndView("auth/secure").setModelContainer(model);
    }

    @Auth
    @WebApi
    @UrlMapping(path = "/api/secure")
    public Map<String, Object> secureApi(MySession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Accès autorisé");
        result.put("user", session.getAttribute("user"));
        result.put("role", session.getAttribute("role"));
        return result;
    }
}
