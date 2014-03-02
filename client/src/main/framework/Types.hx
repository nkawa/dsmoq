package framework;

import promhx.Promise;
import framework.JQuery;

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


typedef Url = String

typedef Application<Page> = {
    toUrl: Page -> Url,
    fromUrl: Url -> Page,
    draw: Page -> Rendered<Void, Page>
}
enum Unit {}
enum Signal {Signal;}
