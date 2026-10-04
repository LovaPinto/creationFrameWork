package Controller;

import com.lovapinto.Autowired;
import com.lovapinto.MyController;
import com.lovapinto.Model;
import com.lovapinto.ModelAndView;
import com.lovapinto.UrlMapping;
import com.lovapinto.WebApi;
import Dto.ApiInfo;
import Repository.UserRepository;

import java.util.List;

@MyController
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @UrlMapping(path = "/user")
    public ModelAndView list(Model model) {
        List<String> users = userRepository.findAll();
        model.setAttribute("users", users);

        return new ModelAndView("users/list")
                .setModelContainer(model);
    }

    @WebApi
    @UrlMapping(path = "/api/users")
    public List<String> listJson() {
        return userRepository.findAll();
    }

    @WebApi
    @UrlMapping(path = "/api/info")
    public ApiInfo info() {
        return new ApiInfo("lovapinto", "sprint6",
                List.of("/api/users", "/api/info", "/api/ping"));
    }
}
