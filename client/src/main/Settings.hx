class Settings{
    public static var api = new ApiUrl();

    public static var groups = [
    {value: "73855cc3-753a-4893-acc7-1f1c4453fb1b", displayName: "terurou"},
    {value: "4d3c781e-6591-4f1f-a30c-ec1d2a991644", displayName: "t_okada"},
    {value: "cb898991-9f49-4574-8659-a8379390dc5d", displayName: "maeda_ "},
    {value: "aed619f9-92f9-4298-a079-707594b72341", displayName: "kawagti"}
    ];

    public static function findNameById(id){
        return groups.filter(function(x){return x.value== id;})[0].displayName;
    }
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

    public function datasetChangeAcl(datasetId: String, groupId: String){
        return '/api/datasets/$datasetId/acl/$groupId';
    }
    public function datasetDeleteAcl(datasetId: String, groupId: String){
        return '/api/datasets/$datasetId/acl/$groupId';
    }
    public function datasetDefaultAccess(datasetId: String){
        return '/api/datasets/${datasetId}/acl/guest';
    }
/*
    public function datasetChangeAcl(datasetId: String, itemId: String){
        return "/resources/dummy/api/ok.json";
    }
    public function datasetDeleteAcl(datasetId: String, itemId: String){
        return "/resources/dummy/api/ok.json";
    }
    public function datasetDefaultAccess(datasetId: String){
        return "/resources/dummy/api/ok.json";
    }
    */
}
