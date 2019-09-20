# gatling-perf

### Install
- Clone the repo
- Install Java if you don't have it
    
### Define env.conf
You'll need to add a gitignored config file: `/conf/env.conf`. The model is:
```
local1 {
  baseURL = "http://local1.marqeta.com/v3"
  authUser = "admin_consumer"
  authPassword = admin_pass
}
```

### Common Install Issues:
  - make sure to add jEnv to your $PATH ([jEnv installation page. Use the 'Mac OS X via Homebrew' installation instructions.](http://www.jenv.be/))
  - check which versions of java are installed on your system with `/usr/libexec/java_home -V`
  - some people must manually `mkdir -p ~/.jenv/versions` before adding java versions with jenv
  - set the global java version to use with `jenv global <version path>`

### Run
From `/gatling-perf` execute `$ cd bin/ && JAVA_OPTS="-Denv=mq-qa" sh gatling.sh -rf results/mq-qa/<path-to-desired-results>/`, and you'll get a list of simulations to run. Select the simulation.

You can also use the `-s` option if you now what test you want to run; e.g., `$ JAVA_OPTS="-Denv=mq-qa" sh bin/gatling.sh -rf results/mq-qa/<path-to-desired-results>// -s regressions.OriginalRegression.scala`

