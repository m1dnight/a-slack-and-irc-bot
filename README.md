# Clojo

Clojo is a bot framework written in Clojure. It is possible to extend it to
connect to any possible chat client. Currently Slack and IRC are supported.  No
I lied, IRC has to be done still. But that's no biggie.

## Installation

Just clone this repository `git clone https://github.com/m1dnight/clojo`. Next
you will have to create a configuration file directory. For example
`~/.clojo`. In there you have to put the minimal config. This is a `clojo.edn`,
`db.edb` and a server to connect to. Examples are shown below and you can fill them out as you please.


### Clojo.edn
```
{
 :instances ["someslack.edn"] ;; Filenames of all the bot config files that you wish to load.
}
```

### Someslack.edn

```
{
 :type  :slack-rtm ;; type of connection: slack-rtm, irc or your own implementation.
 :name  :compsci-slack ;; human readable name
 :token "xoxb-19356396945-pB6fuoLU6D6XVaTAP2foobar" ;; slack token.
 :modules ["google" "karma"] ;; modules to load for this instance.
 }
```

### Db.edn

```
{
 :classname   "org.sqlite.JDBC"
 :subprotocol "sqlite"
 :subname     "/Users/m1dnight/.clojo/clojodb.db" ;; Full path to db
}
```

## Usage

To use the bot you can do one of two things. Run it from `lein` or run it from a
`jar`.

In the former case you will have to change the `project.clj` slightly. It has
two profiles defined on the bottom.

```
  :profiles  {:dev  
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/Users/m1dnight/.clojo"]}
              :deploy             
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/home/christophe/.clojo"]}
              :uberjar
              {:aot :all}})
```

You will have to change them to your own configuration location in order to be
able to run it with your own config.

In the latter case you can just run the `jar` file created by `lein
uberjar`. When you run the jar you can pass the system properties to the java
runtime and override the `project.clj` settings. 

```
CP=/path/to/config
JAR=/path/to/clojo/jar/file/form/uberjar
java -Dconfig.path=$CP -jar $JAR
```

## Plugins

The bot has a very basic but easy to use macro framework which allows you to
easily write plugins. A short introduction on this is coming soon!


## Contributing

If you were to write a new connectivity for this bot or a plugin, you can always
find me on `freenode` under the handle `m1dnight`.

## License

Distributed under the MIT License.
