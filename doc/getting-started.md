# Getting Started with PrimeMover

This guide will walk you through setting up PrimeMover from scratch, even if you're new to Java development.

## Step 1: Install Required Software

### On Ubuntu/Debian Linux:
```bash
# Update your system
sudo apt update && sudo apt upgrade -y

# Install Java 17 (required)
sudo apt install openjdk-17-jdk -y

# Install Maven (required for building)
sudo apt install maven -y

# Install Git (required for version control)
sudo apt install git -y

# Install Chrome browser (required for automation)
wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | sudo apt-key add -
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" | sudo tee /etc/apt/sources.list.d/google-chrome.list
sudo apt update
sudo apt install google-chrome-stable -y

# Install a good text editor (optional but recommended)
sudo apt install code -y  # Visual Studio Code
```





### On Windows:
1. **Install Java 17**: Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://adoptium.net/)
2. **Install Maven**: Download from [Apache Maven](https://maven.apache.org/download.cgi) and follow installation guide
3. **Install Git**: Download from [git-scm.com](https://git-scm.com/download/win)
4. **Install Chrome**: Download from [Google Chrome](https://www.google.com/chrome/)
5. **Install IDE**: Download [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [Visual Studio Code](https://code.visualstudio.com/)

### On macOS:
```bash
# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 17
brew install openjdk@17

# Install Maven
brew install maven

# Install Git
brew install git

# Chrome is usually already installed, if not:
brew install --cask google-chrome
```

### Multilogin X

In addition (for most use cases), you will also need a Multilogin account and Multilogin X installed:

1. log in to your account, go to https://app.multilogin.com/en/home/profiles, and create a first profile (if not done so yet)
2. When clicking for the first time the "Start" button of a profile, you will be prompted to download the Multilogin X (mlx) software (either Linux, Mac or Windows). Download it and install it.
3. When opening a terminal window, type `mlx` and hit enter to start the software (the Multilogin X "agent"). You will have to be logged in to https://app.multilogin.com/en/home/profiles and then start `mlx` to make everything work. The agent will connect through your login in the open browser. 
4. Try starting the profile in the browser again (clicking the Start button again). 

*Note*: sometimes the agent does not properly start up when running `mlx`. Usually, just retyping `mlx` into the terminal and hitting enter again then works.

## Step 2: Verify Your Installation

Open a terminal/command prompt and run these commands to verify everything is installed correctly:

```bash
# Check Java version (should show 17.x.x)
java -version

# Check Maven version (should show 3.6.x or higher)
mvn -version

# Check Git version
git --version

# Check Chrome is accessible
google-chrome --version  # Linux
# OR
"C:\Program Files\Google\Chrome\Application\chrome.exe" --version  # Windows
# OR
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --version  # macOS
```

If any of these commands fail, you need to fix the installation before proceeding.

## Step 3: Get the Code and Build

```bash
# Clone the repository (replace with actual repository URL)
git clone <repository-url>
cd primemover-runner

# Build the entire project (this will take 5-10 minutes the first time)
mvn clean install

# If the build succeeds, you'll see "BUILD SUCCESS" at the end
```

**If the build fails:**
- Check that Java 17 is properly installed and set as default
- Make sure you have internet connection (Maven downloads dependencies)
- Try: `mvn clean install -U` to force update dependencies

## Step 4: Create Your Development Configuration

```bash
# Navigate to the runner module
cd primemover-runner

# Copy the configuration template
cp config/application.properties config/application-local.properties

# Edit the local configuration file
# Use any text editor: nano, vim, code, notepad++, etc.
nano config/application-local.properties  # Linux/macOS
# OR
notepad config/application-local.properties  # Windows
```

**Edit the file to disable external services for local development:**
```properties
# Change these lines in application-local.properties:
spring.profiles.active=local
global.mode=DEVELOP

# Disable external services you don't have accounts for
anticaptcha.enabled=false
multilogin.enabled=false
recorder.enabled=false

# Set shorter timeouts for development
runner.MaxRedoMinutes=5
runner.MaxParallelExecution=1

# Enable debug logging to see what's happening
logging.level.primemover=DEBUG
logging.level.primemover.runner=DEBUG
```

### **Why Create application-local.properties?**

The `application-local.properties` file is a **Spring Boot profile-specific configuration** that transforms your development experience:

**What it does:**
- **Overrides main configuration** - Spring Boot loads `application.properties` first, then overlays your local settings
- **Disables external services** - No need for AntiCaptcha API keys, MultiLogin accounts, or proxy credentials
- **Enables offline development** - Work without internet connectivity to external services
- **Optimizes for development** - Faster startup, single-threaded execution, enhanced logging

**Without local configuration:**
```bash
# Application fails to start because:
# - Missing external service credentials
# - Network connectivity requirements
# - Production timeouts (slow feedback)
# - Complex setup requirements
```

**With local configuration:**
```bash
# Application starts quickly with:
# - No external service dependencies
# - Offline development capability
# - Fast feedback cycles
# - Simplified debugging
```

This is a standard Spring Boot pattern where each environment gets exactly the configuration it needs.

## Step 5: Test Your Setup

### Start the application:
```bash
cd primemover-runner
# From the primemover-runner directory, use the correct command with config location
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"


# You should see logs like:
# "Started PrimeMoverApplication in X.XXX seconds"
# If you see this, the application is running successfully!

# The application will also show debug logs indicating it's using local configuration:
# "loading jobs from http://localhost:8090/api/v1/crawlers-testing"
# This confirms it's using DEVELOP mode with local testing endpoints
```

**Important**: The `-Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"` parameters are crucial because:
- It tells Spring Boot to look for configuration files in the `config/` directory
- Without this, Spring Boot only looks in default locations and won't find your `application-local.properties`
- This ensures your local development settings are actually loaded

### What Happens in DEVELOP Mode

When running with `global.mode=DEVELOP`, the application automatically:

1. **Creates a demo job sequence** that will execute after ~30 seconds:
   - Visit google.ch
   - Search for "Klimaerwärmung ist doof"
   - Wait for results
   - Click a random search result
   - Wait again

2. **Enables testing job types** (Assert jobs for validation)

3. **Uses testing API endpoints** instead of production ones

4. **Adjusts job timing** to today's date for immediate execution

**Important**: Starting the application does **NOT** automatically run tests. It starts the server and executes demo jobs, but the test suite is separate.

### Test basic functionality:
```bash
# In another terminal, test if the application is responding
curl http://localhost:8080/actuator/health

# You should get a response like: {"status":"UP"}
```

### Stop the application:
- Press `Ctrl+C` in the terminal where the application is running

## Step 6: Run Your First Test

### Understanding Application vs Tests

**Two separate processes:**
- **Running the application** = Starts the server with demo jobs
- **Running tests** = Executes the test suite

### Terminal 1: Start the Application (keep running)
```bash
cd primemover-runner
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"
```
This starts the PrimeMover server and keeps it running. You'll see the demo Google search job execute after ~30 seconds.

### Terminal 2: Run Tests (separate terminal)
```bash
cd primemover-runner

# Run all tests
mvn test

# Run specific tests
mvn test -Dtest=PrimemoverApiConverterTest

# Run a test that uses the browser (Chrome will open briefly)
mvn test -Dtest=NavigationHelperTest -Dspring.profiles.active=local

# Run tests with visible browser for debugging
mvn test -Dbrowser.headless=false
```

**Key Points:**
- Keep Terminal 1 running with the application
- Use Terminal 2 to run tests as many times as you want
- Tests run independently of the running application
- You can run different test commands while the app keeps running

### If tests pass, your setup is working correctly!

## Quick Development Workflow

Once you have everything set up, here's your daily workflow:

### Starting Development Work
```bash
# 1. Navigate to project directory
cd primemover

# 2. Pull latest changes
git pull origin main

# 3. Create a new branch for your work
git checkout -b feature/my-new-feature

# 4. Start the application for testing (Terminal 1)
cd primemover-runner
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"
```

### Testing Your Changes
```bash
# In Terminal 2, run specific tests related to your changes
mvn test -Dtest="*YourComponent*Test"

# Run all tests to make sure nothing is broken
mvn test

# Test with a visible browser to see what's happening
mvn test -Dtest=NavigationHelperTest -Dspring.profiles.active=local -Dbrowser.headless=false
```

### Common Development Tasks

**Make a code change and test it:**
```bash
# 1. Edit your Java files in your IDE or text editor
# 2. Restart the application in Terminal 1 (Ctrl+C, then restart)
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"

# 3. Run specific tests in Terminal 2
mvn test -Dtest=YourNewTest
```

**Debug browser automation:**
```bash
# Start with visible browser and debug logging
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/" \
  -Dlogging.level.primemover=DEBUG \
  -Dlogging.level.primemover.util.NavigationHelper=TRACE
```

**Check your code compiles:**
```bash
# Quick compilation check without running tests
mvn compile

# Full build with tests
mvn clean install
```

## Troubleshooting for Beginners

### "Command not found" errors
- **Java not found**: Make sure Java 17 is installed and in your PATH
- **Maven not found**: Make sure Maven is installed and in your PATH
- **Git not found**: Install Git and restart your terminal

### Build failures
```bash
# Try cleaning and rebuilding
mvn clean install -U

# If tests are failing due to browser issues, skip them temporarily
mvn clean install -DskipTests

# Check for Java version issues
mvn -version  # Should show Java 17
```

### Application won't start
- Check that port 8080 is not already in use
- Look at the error logs for specific error messages
- Make sure your `application-local.properties` file is correctly formatted
- Verify you're using the correct Maven command with `-Dspring.config.location=config/`

### Configuration not being loaded
If you see logs showing production endpoints instead of local ones:
```bash
# Make sure you're using the complete command:
mvn spring-boot:run -Dspring.profiles.active=local -Dspring.config.location=config/

# Check that your application-local.properties file exists:
ls -la config/application-local.properties

# Verify the file contains the correct settings:
cat config/application-local.properties | grep global.mode
# Should show: global.mode=DEVELOP
```

### Browser tests fail
- Make sure Chrome is installed and accessible
- Try running with visible browser: add `-Dbrowser.headless=false` to your test commands
- Check that no other browser instances are interfering

### "Permission denied" errors
- On Linux/macOS, you might need to make scripts executable: `chmod +x script-name`
- Make sure you have write permissions in the project directory

### Demo job not executing
If you don't see the Google search demo job after 30 seconds:
- Check that the application is polling for jobs (you should see logs every 60 seconds)
- Verify `global.mode=DEVELOP` is set in your local configuration
- Look for error messages in the application logs

## What to Do Next

Once you have the basic setup working:

1. **Watch the demo job execute**: See the automated Google search in action
2. **Explore the code**: Start by looking at the job classes in `src/main/java/primemover/model/jobs/`
3. **Read the tests**: Look at test files to understand how components work
4. **Try modifying a simple job**: Start with `WaitJob` or `VisitJob` to understand the structure
5. **Run with visible browser**: Use `-Dbrowser.headless=false` to see what the automation is doing
6. **Check the logs**: Enable debug logging to understand the execution flow

## Getting Help

If you get stuck:
1. **Check the logs**: Most issues are explained in the application logs
2. **Look at existing tests**: They show how components are supposed to work
3. **Start simple**: Begin with basic jobs before trying complex automation
4. **Use visible browser**: See what's actually happening during automation
5. **Use two terminals**: Keep the application running in one, run tests in another

## Next Steps

- Read the [Architecture Overview](architecture.md) to understand the system design
- Check out the [Job System Guide](job-system.md) to learn about creating and configuring jobs
- Review the [Development Guide](development.md) for advanced development workflows
