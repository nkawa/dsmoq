package dsmoq.framework;

import promhx.Promise;
import dsmoq.framework.JQuery;
import dsmoq.framework.Effect;

typedef Html = Jq

typedef Application<TPage: EnumValue> = {
    initialize: Void -> Replacable<Html, Void, Void>,
    toUrl: TPage -> PageInfo,
    fromUrl: PageInfo -> TPage,
    draw: TPage -> Rendered<Void, TPage>
}

typedef Rendered<TState, TEvent> = {
    html: Html,
    event: Promise<TEvent>,
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
