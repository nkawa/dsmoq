package pages;

import components.Pagination;
import framework.helpers.Connection;
import framework.helpers.*;
import pages.Models;
import framework.Types;

typedef User = {userId: String, userName: String}
typedef GroupId = String

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

    private static function fromAclLevel(l: AclLevel): Int{
        return switch(l){
            case(AclLevel.LimitedPublic): 1;
            case(AclLevel.FullPublic):    2;
            case(AclLevel.Owner):         3;
        };
    }

    public static function datasetsAclAdd(datasetId, groupId, level){
        return {
            method: HttpMethod.Post,
            url: Settings.api.datasetAddAcl(datasetId),
            params: {id: groupId, level: fromAclLevel(level) }
        };
    }

    public static function datasetsAclDelete(datasetId, groupId){
        return {
            method: HttpMethod.Delete,
            url: Settings.api.datasetDeleteAcl(datasetId, datasetId),
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
            throw 'Status was not OK: ${Std.string(json)}';
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

    public static function sendDatasetsAclAdd(
        datasetId: String, groupId: String, level: AclLevel
    ): HttpProcess<AclGroup>{
        return Connection.then(
            Connection.send(datasetsAclAdd(datasetId, groupId, level)), extractData.bind(_, "")
        );
    }

    public static function sendDatasetsAclDelete(
        datasetId: String, groupId: String
    ): HttpProcess<Dynamic>{
        return Connection.then(
            Connection.send(datasetsAclDelete(datasetId, groupId)), extractData.bind(_, "")
        );
    }
}
