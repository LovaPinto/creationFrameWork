package Controller;

import com.lovapinto.MyController;
import com.lovapinto.Model;
import com.lovapinto.ModelAndView;
import com.lovapinto.UrlMapping;
import Repository.UserRepository;

import java.util.List;

@MyController
public class UserController {

    private final UserRepository userRepository = new UserRepository();

    @UrlMapping(path = "/user")
    public ModelAndView list(Model model) {
        List<String> users = userRepository.findAll();
        model.setAttribute("users", users);

        return new ModelAndView("users/list")
                .setModelContainer(model);
    }
}
