package Controller;

import com.lovapinto.MyController;
import com.lovapinto.UrlMapping;
import com.lovapinto.WebApi;

@MyController
@WebApi
public class ApiController {

    @UrlMapping(path = "/api/ping")
    public String ping() {
        return "pong";
    }
}
