class Settings{
    public static var api = new ApiUrl();
}

private class ApiUrl{
    public function new(){}
    public var profile = "/api/profile";
    public var datasetList = "/api/datasets";
    public var datasetNewPost = "/api/datasets";
    public function dataset(id: String) { return '/api/datasets/$id';}

//    public var profile = "/resources/dummy/api/profile.json";
//    public var profile = "/resources/dummy/api/profile_anonym.json";
//    public var datasetList = "/resources/dummy/api/datasets.json";
//    public function dataset(id: String) { return '/resources/dummy/api/datasets_1.json';}

    public var login = "/api/signin";
 //   public var login = "/resources/dummy/api/login.json";
//    public var login = "/resources/dummy/api/not_login.json";
    public var logout = "/api/signout";
//    public var logout = "/resources/dummy/api/login.json";

    /*
    public function datasetAddAcl(datasetId: String){
        return '/api/datasets/$datasetId/acl';
    }
    public function datasetChangeAcl(datasetId: String, itemId: String){
        return '/api/datasets/$datasetId/acl/$itemId';
    }
    public function datasetRemoveAcl(datasetId: String, itemId: String){
        return '/api/datasets/$datasetId/acl/$itemId';
    }
    */
    public function datasetAddAcl(datasetId: String){
        return "/resources/dummy/api/acl_ok.json";
    }
    public function datasetChangeAcl(datasetId: String, itemId: String){
        return "/resources/dummy/api/ok.json";
    }
    public function datasetDeleteAcl(datasetId: String, itemId: String){
        return "/resources/dummy/api/ok.json";
    }
}
