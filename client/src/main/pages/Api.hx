package pages;

import components.Pagination;
import framework.helpers.Connection;
import framework.helpers.*;
import pages.Models;

typedef User = {userId: String, userName: String}

enum LoginStatus {
    Anonymouse;
    LogedIn(user: User);
}

typedef JsonResponse<A> = {status: String, ?data: A, ?message: String}

class Api{
    public static var profile = {
        method: HttpMethod.Get,
        url: Settings.api.profile,
        params:{}
    };

    public static function datasetsList(req: PagingRequest){
        return {
            url: Settings.api.datasetList,
            method: HttpMethod.Get,
            params:Core.merge(req, {})
        };
    }

    public static function datasetDetail(id: String) {
        return {
            method: HttpMethod.Get,
            url: Settings.api.dataset(id),
            params: {}
        };
    }

    public static function extractData<A>(json: JsonResponse<A>, field = ""):Null<A>{
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

    public static function extractProfile(json: Dynamic){
        var user = extractData(json);
        return if(user == null || user.isGuest ){
            Anonymouse;
        }else{
            LogedIn({userId: user.id, userName: user.name});
        }
    }

    public static function sendDatasetsList(req: PagingRequest): HttpProcess<MassResult<Array<DatasetSummary>>>{
        return Connection.then(
            Connection.send(datasetsList(req)),
            extractData.bind(_, "")
        );
    }
}
