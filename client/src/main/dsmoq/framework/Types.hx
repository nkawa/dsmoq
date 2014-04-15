package dsmoq.framework;

import promhx.Promise;
import dsmoq.framework.JQuery;
import dsmoq.framework.Effect;

typedef Html = Jq

typedef Rendered<State, Out> = {
  html: Html,
  event: Promise<Out>,
  state: Void -> State
}

typedef Component<In, State, Out> = {
  render: In -> Rendered<State, Out>
}

enum NextChange<I,O> { Inner(a: I); Outer(b: O); }
typedef Foldable<In, St, Out> = Component<In, St, NextChange<In, Out>>
typedef Replacable<In, St, Out> = {> Rendered<St, Out>, put: In -> Void}
typedef PlaceHolder<In, St, Out> = { render: In -> Replacable<In, St, Out> }

typedef Application<Page> = {
    initialize: Page -> Replacable<Page, Void, Void>,
    toUrl: Page -> PageInfo,
    fromUrl: PageInfo -> Page,
    draw: Page -> Rendered<Void, Page>
}

enum Unit {}
enum Signal {Signal;}

typedef Selector = String
typedef Json = Dynamic

typedef Option<A> = haxe.ds.Option<A>;
