package dsmoq.framework;

import promhx.Stream;
import dsmoq.framework.JQuery;
import dsmoq.framework.Effect;

typedef Html = Jq

typedef Application<TPage: EnumValue> = {
    initialize: Void -> Replacable<Html, Void, PageEvent<TPage>>,
    toUrl: TPage -> PageInfo,
    fromUrl: PageInfo -> TPage,
    render: TPage -> Option<Html> -> Rendered<Void, PageEvent<TPage>>
}

enum PageEvent<TPage: EnumValue> {
    Navigate(page: TPage);
    NavigateAsBackword(page: TPage);
    Foward;
    Backward;
    //TODO APIレスポンスでログアウト等を検知したときに処理が必要
}

typedef Rendered<TState, TEvent> = {
    html: Html,
    event: Stream<TEvent>,
    state: Void -> TState
}

typedef Component<TIn, TState, TEvent> = {
    render: TIn -> Rendered<TState, TEvent>
}
typedef PlaceHolder<TIn, TState, TEvent> = {
    render: TIn -> Replacable<TIn, TState, TEvent>
}

enum NextChange<I,O> { Inner(a: I); Outer(b: O); }
typedef Foldable<TIn, TState, Out> = Component<TIn, TState, NextChange<TIn, Out>>
typedef Replacable<TIn, TState, TEvent> = {> Rendered<TState, TEvent>, put: TIn -> Void}

enum Unit {}
enum Signal {Signal;}

typedef Selector = String
typedef Json = Dynamic

typedef Option<A> = haxe.ds.Option<A>;
