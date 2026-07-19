package Repository;

import com.lovapinto.RepositoryAnnotation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@RepositoryAnnotation
public class UserRepository {

    private Connection connection;

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public List<String> findAll() {
        List<String> users = new ArrayList<>();
        if (connection == null) {
            return users;
        }
        try {
            String sql = "SELECT name FROM users";
            PreparedStatement stmt = connection.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }
}
