package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.JQuery;

import components.Clickable;
import framework.helpers.*;
using framework.helpers.Components;

class Stub{
    public static function render(templateName: String){
        return untyped Templates.create(templateName).render({});
    }
}
