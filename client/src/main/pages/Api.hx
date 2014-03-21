package pages;

import components.Pagination;
import framework.helpers.Connection;
import framework.helpers.*;
import pages.Models;
import framework.Types;


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
        return send(datasetsList(req));
    }

    public static function sendDatasetsAclAdd( datasetId: String, groupId: String, level: AclLevel): HttpProcess<AclGroup>{
        return send( datasetsAclAdd(datasetId, groupId, level));
    }

    public static function sendDatasetsAclDelete( datasetId: String, groupId: String): HttpProcess<Dynamic>{
        return send(datasetsAclDelete(datasetId, groupId));
    }

    public static function sendDatasetsAclChange( datasetId: String, groupId: String, level: AclLevel): HttpProcess<Dynamic>{
        return send(datasetsAclChange(datasetId, groupId, level));
    }

    public static function sendDatasetsDefaultAccess( datasetId: String, level:DefaultLevel ): HttpProcess<Dynamic>{
        return send(datasetsDefaultAccessChange(datasetId, level));
    }

    private static function send<A>(request): HttpProcess<A>{
        return Connection.then(Connection.send(request), extractData.bind(_, ""));
    }

    private static function fromAclLevel(l: AclLevel): Int{
        return switch(l){
            case(AclLevel.LimitedPublic): 1;
            case(AclLevel.FullPublic):    2;
            case(AclLevel.Owner):         3;
        };
    }

    private static function datasetsAclAdd(datasetId, groupId, level){
        return {
            method: HttpMethod.Post,
            url: Settings.api.datasetAddAcl(datasetId),
            params: {id: groupId, level: fromAclLevel(level) }
        };
    }

    private static function datasetsAclDelete(datasetId, groupId){
        return {
            method: HttpMethod.Delete,
            url: Settings.api.datasetDeleteAcl(datasetId, groupId),
            params: {}
        };
    }

    private static function datasetsAclChange(datasetId, groupId, level){
        return {
            method: HttpMethod.Put,
            url: Settings.api.datasetChangeAcl(datasetId, groupId),
            params: {level: fromAclLevel(level)}
        };
    }

    private static function fromDefaultLevel(l: DefaultLevel): Int{
        return switch(l){
            case(DefaultLevel.Deny): 0;
            case(DefaultLevel.LimitedPublic): 1;
            case(DefaultLevel.FullPublic): 2;
        };
    }

    private static function datasetsDefaultAccessChange(datasetId, level){
        return {
            method: HttpMethod.Put,
            url: Settings.api.datasetDefaultAccess(datasetId),
            params: {level: fromDefaultLevel(level)}
        };
    }
}
