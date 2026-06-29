package Controller;

import com.lovapinto.MyController;
import com.lovapinto.UrlMapping;

@MyController
public class UserController {
    @UrlMapping(path="/login")
    public void login(HttpServletRequest req, HttpServletResponse res) { }

    @UrlMapping(path="/register")
    public void register(HttpServletRequest req, HttpServletResponse res) { }
 
}
