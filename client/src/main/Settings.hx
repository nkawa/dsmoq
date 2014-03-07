class Settings{
    public static var api = new ApiUrl();
}

private class ApiUrl{
    public function new(){}
//    public var login = "/api/signin";
    public var login = "/resources/dummy/api/login.json";
//    public var login = "/resources/dummy/api/not_login.json";
//    public var logout = "/api/signout";
    public var logout = "/resources/dummy/api/login.json";
}
