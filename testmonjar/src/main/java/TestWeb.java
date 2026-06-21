import java.io.IOException;
import java.io.PrintWriter;
import com.lovapinto.SampleServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class TestWeb {
     public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            String url= SampleServlet.processeRequest(req, res);
        res.setContentType("text/plain");
        PrintWriter out = res.getWriter();
        out.println("URL complète : " + req.getRequestURL());
    }
}
