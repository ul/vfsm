# VFSM

[](dependency)
```clojure
[vfsm "0.1.0"] ;; latest release
```
[](/dependency)

VFSM in Clojure(Script), with spec compilation from GraphML sources.

## Usage

##### Draw spec in [yEd][1], following next conventions:

* Text of **node label** contains state name followed by its actions (if any):
```
NAME OF STATE (1)
E: action1, action2 (2)
X: ... (3)
I: condition1 => action1, condition2 => action2 (4)
```
1. **state name,** will be dasherized to `:name-of-state`
2. **entry action(s),** fn names, commas are optional
3. **exit action(s),** the same as above
4. **input action(s),** conditions must be functions of inputs

* Text of **edge label** represents **transition condition** from state to state.
It must be a function of the one argument which is current inputs.
Because inputs are usually a map, for simple checks you can benefit from the fact that Clojure keywords are functions.
To check condition in specific order, put numbers before them.

##### Require vfsm:
```clojure
[vfsm.core :as vfsm]
[vfsm.graphml :refer [compile-spec]]
```

##### Compile spec in your Clojure/Clojurescript source:
```clojure
(def spec (compile-spec "path/to/your/spec/in/resources.graphml"))
```

##### Put meaning into actions and start atomaton.
Usually you want to provide action functions in the same namespace where you compile spec.
Actions must take to params: context and inputs.
To bind given watchable inputs to automaton execution:
```clojure
(vfsm/start rtdb spec context)
```

## TODO

[x] Explicit transitions traverse order

[ ] More elegant input actions format

[ ] Tests

[ ] Examples

[ ] Hunting down and eliminating boilerplate of connecting VFSM with the rest of the app

## Contribution

... is welcome and very much appreciated.
Feel free to ping me with questions and to make PR.

## License

Copyright © 2015 Ruslan Prokopchuk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://www.yworks.com/en/products/yfiles/yed/