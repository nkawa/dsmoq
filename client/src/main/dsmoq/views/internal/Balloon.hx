package dsmoq.views.internal;

import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.js.JqHtml;
import js.Browser;
import js.html.Event;

class Balloon {
    public static function show(target: Html, positon: BalloonPosition, ?autoClose = true) {
        var tipNode = target.find(".balloon-tip");
        if (tipNode.length <= 0) {
            tipNode = JQuery._("<div class=\"balloon-tip\"></div>");
            target.append(tipNode);
        }

        var p = convertPosition(positon);

        var x = p.x;
        var y = p.y;
        var width = target.outerWidth();
        var height = target.outerHeight();
        var orientation = p.orientation;

        target
            .css("visibility", "visible")
            .css(switch (orientation) {
                case TopLeft, BottomLeft:
                    { left: '${x - width + 18}px', right: "auto" };
                case TopCenter, BottomCenter:
                    { left: '${x - width / 2}px', right: "auto" };
                case TopRight, BottomRight:
                    { left: '${x - 18}px', right: "auto" };
                case LeftTop, LeftCenter, LeftBottom:
                    { left: '${x - width - 18}px', right: "auto" };
                case RightTop, RightCenter, RightBottom:
                    { left: '${x + 18}px', right: "auto" };
            })
            .css(switch (orientation) {
                case TopLeft, TopCenter, TopRight:
                    { top: '${y - height - 18}px', bottom: 'auto' };
                case BottomLeft, BottomCenter, BottomRight:
                    { top: '${y + 18}px', bottom: 'auto' };
                case LeftTop, RightTop:
                    { top: '${y - height + 18}px', bottom: 'auto' };
                case LeftCenter, RightCenter:
                    { top: '${y - height / 2}px', bottom: 'auto' };
                case LeftBottom, RightBottom:
                    { top: '${y - 18}px', bottom: 'auto' };
            });
        ZIndexManager.push(target, Balloon.hide.bind(target, true));

        untyped target.__parent = target.parent();
        target.appendTo("body");

        tipNode
            .addClass(function (i, x) {
                return ~/balloon-tip-\S+/g.replace(x, "") + " balloon-tip-" + switch (orientation) {
                    case TopLeft, TopCenter, TopRight: "down";
                    case BottomLeft, BottomCenter, BottomRight: "up";
                    case LeftTop, LeftCenter, LeftBottom: "right";
                    case RightTop, RightCenter, RightBottom: "left";
                }
            })
            .css(switch (orientation) {
                case TopLeft, BottomLeft:
                    { left: "auto", right: "8px" };
                case TopCenter, BottomCenter:
                    { left: '${width / 2 - 10}px', right: "auto" };
                case TopRight, BottomRight:
                    { left: "8px", right: "auto" };
                case LeftTop, LeftCenter, LeftBottom:
                    { left: "auto", right: "-20px" };
                case RightTop, RightCenter, RightBottom:
                    { left: '-20px', right: "auto" };
            })
            .css(switch (orientation) {
                case TopLeft, TopCenter, TopRight:
                    { top: "100%", bottom: "auto" };
                case BottomLeft, BottomCenter, BottomRight:
                    { top: "auto", bottom: "100%" };
                case LeftTop, RightTop:
                    { top: "auto", bottom: "8px" };
                case LeftCenter, RightCenter:
                    { top: '${height / 2 - 10}px', bottom: "auto" };
                case LeftBottom, RightBottom:
                    { top: "8px", bottom: "auto" };
            });

        if (autoClose) {
            function hideOnMousedown(event: Event) {
                if (target.has(cast event.target).length <= 0) {
                    Balloon.hide(target, true);
                }
            }
            target.one("hide.accordion", function onHide(_) {
                Browser.document.removeEventListener("mousedown", hideOnMousedown, true);
            });
            Browser.document.addEventListener("mousedown", hideOnMousedown, true);
        }
    }

    public static function hide(target: Html, canTrigger: Bool): Void {
        target.css({
            visibility: "hidden",
            left: "-10000px",
            top: "-10000px",
            zIndex: ""
        });
        if (target.parent() != untyped target.__parent) {
            target.appendTo(untyped target.__parent);
            untyped target.__parent = null;
        }
        ZIndexManager.pop(target);
        if (canTrigger) {
            JsTools.setImmediate(function () {
                target.trigger("accordion:destroyed");
            });
        }
    }

    static function convertPosition(position: BalloonPosition) {
        return switch (position) {
            case Absolute(x, y, orientation):
                {
                    x: x,
                    y: y,
                    orientation: orientation
                };
            case Relative(target, orientation):
                if (target.length <= 0) throw new Error('target is empty');

                var offset = target.offset();
                switch (orientation) {
                    case TopLeft, TopCenter, TopRight:
                        {
                            x: offset.left + Std.int(target.outerWidth() / 2),
                            y: offset.top,
                            orientation: orientation
                        }
                    case BottomLeft, BottomCenter, BottomRight:
                        {
                            x: offset.left + Std.int(target.outerWidth() / 2),
                            y: offset.top + target.outerHeight(),
                            orientation: orientation
                        }
                    case LeftTop, LeftCenter, LeftBottom:
                        {
                            x: offset.left,
                            y: offset.top + Std.int(target.outerHeight() / 2),
                            orientation: orientation
                        }
                    case RightTop, RightCenter, RightBottom:
                        {
                            x: offset.left + target.outerWidth(),
                            y: offset.top + Std.int(target.outerHeight() / 2),
                            orientation: orientation
                        }
                }
        }
    }

    public static function positionFromAttribute(target: JqHtml, props: Dynamic): BalloonPosition{
        var propString = props.position;

        var orientation = try{
            Type.createEnum(BalloonOrientation, propString);
        }catch(_: Dynamic){
            BalloonOrientation.RightCenter;
        };

        return BalloonPosition.Relative(target, orientation);
    }
}

enum BalloonPosition {
    Absolute(x: Int, y: Int, orientation: BalloonOrientation);
    Relative(target: Html, orientation: BalloonOrientation);
}

enum BalloonOrientation {
    TopLeft;
    TopCenter;
    TopRight;
    BottomLeft;
    BottomCenter;
    BottomRight;
    LeftTop;
    LeftCenter;
    LeftBottom;
    RightTop;
    RightCenter;
    RightBottom;
}
