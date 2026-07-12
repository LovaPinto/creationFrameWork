package Repository;

import java.util.List;

public class UserRepository {

    public List<String> findAll() {
        return List.of("Alice", "Bob", "Charlie");
    }
}
