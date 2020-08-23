# blog-feedback-app

A small [ClojureScript](https://clojurescript.org/) +
[Rum](https://github.com/tonsky/rum) +
[DataScript](https://github.com/tonsky/datascript) app used as an example
for a blog post, to write feedback to that blog post.

## Idea

Attach a script to a post that make it possible for readers to write feedback
to that post.

## App design

The application is based on the structure of the posts of my blog:

```HTML
  <div class='body'>
    <div class='section' id='section-title'>
      <h2>Section title</h2>
      <p>Text<p/>
    </div>
  </div>
```

At start the script looks for elements with `class="section"` and, for each of
those elements, gets the id and title of the section and attach a feedback
component to it.

Each component, attached to a post section, adds a feedback form to add new
comments and a div element with a list of previous comments to which a reader
can reply to.

Feedback data should be stored in a DataScript database and the components will
react to changes of the database.

Database schema:

```clojure
(def schema
  {:section/id {:db/unique :db.unique/identity}
   :comment/id {:db/unique :db.unique/identity}
   :comment/section {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/one}
   :comment/parent {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one}})
```

## How to run the application

This project includes an example post in resources/public/index.html to be
used as a base during development.

To run the application you need the [clj](https://clojure.org/guides/getting_started)
 command line tool:

```sh
$ clj -A:fig:build
```

This will generate the js code and will open the default browser with the
application with a [Figwheel](https://figwheel.org/) session and a REPL to
interact with it.

At this point you are ready to write feedbacks in the page and interact with
the application within the REPL session.

To create a release build run:

```sh
$ clj -A:fig:min
```

When the build will finish, the application file will be available at
`target/public/cljs-out/dev-main.js`, ready to be embedded in your page.

## Current state

Right now this is just a browser application and there is no backend but it is,
more or less, ready by design to interact with a server side application.
Changes to the database are sent as messages to a channel and a listener gets
messages out of it and updates the database.
It should not take much time to send events to a backend and to subscribe to a
websocket to get realtime updates.

## Next steps

Add a backend with:

- a durable storage
- an API to get previous messages
- an API to create new messages
- an API to delete messages
- a websocket for realtime communication
- optionally an authentication system

Enough for the README.md, more details in the upcoming post.
