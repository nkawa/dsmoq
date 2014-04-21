package dsmoq.pages;

import promhx.Promise;
import dsmoq.pages.Definitions;
import dsmoq.framework.JQuery;

import dsmoq.components.Clickable;
import dsmoq.framework.helpers.*;
using dsmoq.framework.helpers.Components;

class Stub{
    public static function render(templateName: String){
        return untyped Templates.create(templateName).render({});
    }
}
