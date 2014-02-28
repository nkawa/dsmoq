package pages;

typedef User = {userId: String, userName: String}

enum LoginStatus {
    Anonymouse;
    LogedIn(user: User);
}

typedef JsonResponse = {status: String, ?data: Dynamic, ?message: String}

class Auth{
    public static function extractData<A>(json: JsonResponse, field = ""):Null<A>{
        return if(json.status.toUpperCase() == "OK"){
            if(json.data == null){
                throw "status was OK, but no data";
            };
            if(field == ""){
                json.data;
            }else{
                if(Reflect.hasField(json.data, field)){
                    Reflect.field(json.data, field);
                }else{
                    null;
                }
            }
        }else{
            throw json.message;
        }
    }

    public static function extractUser(json: JsonResponse){
        var user = extractData(json, "user");
        return if(user == null){
            Anonymouse;
        }else{
            LogedIn(user);
        }
    }
}
