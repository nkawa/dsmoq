package framework;

import promhx.Promise;
import framework.JQuery;

typedef Html = Dynamic // temporary jQuery object

typedef Rendered<State, Out> = {
  html: Html,
  event: Promise<Out>,
  state: Void -> State
}

typedef Component<In, State, Out> = {
  render: In -> Rendered<State, Out>
}

typedef Url = String

typedef Application<Page> = {
    toUrl: Page -> Url,
    fromUrl: Url -> Page,
    draw: Page -> Rendered<Void, Page>
}

