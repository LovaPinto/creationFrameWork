package Controller;

import com.lovapinto.Autowired;
import com.lovapinto.MyController;
import com.lovapinto.Model;
import com.lovapinto.ModelAndView;
import com.lovapinto.MyRequestParam;
import com.lovapinto.UrlMapping;
import com.lovapinto.WebApi;
import Dto.User;
import Repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @UrlMapping(path = "/test/binding-simple", method = "POST")
    public Map<String, Object> testBindingSimple(
            @MyRequestParam("name") String name,
            @MyRequestParam("age") int age) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("age", age);
        return result;
    }


    @WebApi
    @UrlMapping(path = "/test/binding-dto", method = "POST")
    public User testBindingDto(@MyRequestParam("user") User user) {
        return user;
    }

    @UrlMapping(path = "/form")
    public ModelAndView formSimple() {
        return new ModelAndView("users/form-simple");
    }

    @UrlMapping(path = "/form/object")
    public ModelAndView formDto() {
        return new ModelAndView("users/form-dto");
    }

    @UrlMapping(path = "/afficher/user")
    public ModelAndView userDetailAndView(
            @MyRequestParam("name") String name,
            @MyRequestParam("age") int age) {
        Model model = new Model();
        User user = new User();
        user.setName(name);
        user.setAge(age);
        model.setAttribute("user", user);
        return new ModelAndView("users/detail")
                .setModelContainer(model);
    }
}
