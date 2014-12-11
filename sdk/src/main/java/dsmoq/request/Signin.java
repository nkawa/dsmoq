package dsmoq.request;

public class Signin {
    private String id;
    private String password;

    public Signin() {
    }

    public Signin(String id, String password) {
        this.id = id;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Signin signin = (Signin) o;

        if (id != null ? !id.equals(signin.id) : signin.id != null) return false;
        if (password != null ? !password.equals(signin.password) : signin.password != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}
