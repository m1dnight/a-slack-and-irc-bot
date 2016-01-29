# Writing modules

Writing a module for Clojo is rather straightforward, given that you have some
knowledge of  Clojure. There are two types of modules. First there are triggers
and second of all there are listeners. The former are modules that can be
invoked with a command (e.g., `~google`) and some arguments (e.g., `~google
something`).

The latter are commands that get executed on each message that is sent on a
channel. For example, a module that logs all messages on a channel.

## Skeleton

```
(ns clojo.modules.plugins.myplugin
  (:require [clojure.tools.logging :as    log]
            [clojo.modules.macros  :as      m]))

(m/defmodule                                                                                   
  :mymodule
  0 ;; Timeout
  ...)
```

The above skeleton shows the minimal required code for a module. Let's assume
this is module `myplugin.clj`. The first thing to do is make sure the namespace
suffix matches your filename. The second thing you need to do is insert eh
`m/module` skeleton. This is where you can add as many commands as you wish. The
first argument to `defmodule` is a human-readable name whose purpose is
debugging. The second argument is an integer which is the number of milliseconds
you wish to be between subsequent invocations.


## Triggers

We will add a trigger to the above skeleton that simply echo's back the
arguments it got. To do this we add the `defcommand` function in the body where
the `...` were previously. The first argument to `defcommand` is the name of the
command the user has to type in the chat channel to invoke it, in this case that
would be `echo`. The second argument is a function which has to have exactly 3
arguments.

+ `instance` is the actual bot instance. This argument is currently needed to
properly be able to reply and such. You should only use it to pass to other
macro functions. Advanced macros can perhaps use it as persistent storage but
that will not work for the time being. Just leave it as it is. (Patches welcome :>)

+ `args` are the arguments. It is just a string parsed from the raw command.
E.g., given the invocation `~google foo bar baz`, the `args` variable will
contain `"foo bar baz"`.

+ `msg` is the original message. It is a Clojure persistent hashmap which
contains the details of the actual message.
```
{:message ;; Original text message
 :channel ;; The channel the message came from.
 :userid  ;; Unique identifier of the sending user
 :nick    ;; Nickname of the sender (does not work in Slack, yet)
 :server  ;; Server or Slack team.
 :command :PRIVMSG ;; this is needed to trigger handlers for all public messages.
 }
```

So our final module should look something like the following.

```
(ns clojo.modules.plugins.myplugin
  (:require [clojure.tools.logging :as    log]
            [clojo.modules.macros  :as      m]))

(m/defmodule                                                                                   
  :mymodule
  0 ;; Timeout
  (m/defcommand
    "echo"
    (fn [instance args msg]
      (m/reply instance msg args))))
```

You can see that we have yet another macro, namely the `reply` macro. It is a
shorthand to send a message to the channel the message originated from. The
first two arguments, namely `instance` and `msg` are mandatory. The last
argument should be a string containing the message you want to send to the
channel.


## Listeners

Recall the skeleton code from before.

```
(ns clojo.modules.plugins.myplugin
  (:require [clojure.tools.logging :as    log]
            [clojo.modules.macros  :as      m]))

(m/defmodule                                                                                   
  :mymodule
  0 ;; Timeout
  ...)
```

This time we want a module that listens to *all* messages that are being sent on
the channels the bot is on. For example, if somebody uses the word "fuck" we
might want to warn them about their foul language. The internet is a nice fluffy
place and we don't have to stand for that kind of language.

```
(m/defhook
  :PRIVMSG
  (fn [instance msg]
    (let [dirtywords (re-seq #"fuck" (:message msg))]
      (when dirtywords
        (m/reply instance msg "No swearing, ffs.")))))
```

For that we have the macro named `defhook`. It takes as a first argument the
`:PRIVMSG` keyword. It is a definition of what kinds of messages this bot
responds to. Just leave it for now. The feature is only useful for IRC
connections and we currently do not support IRC yet. The second argument is a
function that takes exactly two arguments. The instance and message. Just as for
a command.


## Multiple hooks and commands

If you like, you can add as many commands and/or hooks in a single module as you
like. If we combine the code from before we can create a single module that does
both.

```
(ns clojo.modules.plugins.myplugin
  (:require [clojure.tools.logging :as    log]
            [clojo.modules.macros  :as      m]))

(m/defmodule                                                                                 
  :mymodule
  0 ;; Timeout
  (m/defhook
    :PRIVMSG
    (fn [instance msg]
      (let [dirtywords (re-seq #"fuck" (:message msg))]
        (when dirtywords
          (m/reply instance msg "No swearing, ffs.")))))
  (m/defcommand
    "echo"
    (fn [instance args msg]
      (m/reply instance msg args))))
```

For more inspiration or help you can consult the modules directory in the
repository.
