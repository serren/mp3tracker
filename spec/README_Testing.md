# Module 3: Testing

## Table of contents

- [What to do](#what-to-do)
- [Sub-task 1: Testing strategy](#sub-task-1-testing-strategy)
- [Sub-task 2: Perform different types of testing](#sub-task-2-perform-different-types-of-testing)

## What to do

In this module, you need to adjust the services by adding tests.

## Sub-task 1: Testing strategy

1. Develop a testing strategy and describe the approach to ensure application stability and testing coverage:
    - Unit tests
    - Integration tests
    - Component tests
    - Contract tests
    - End-to-end tests

2. Write a short document explaining the chosen approach and how the combination of strategies will help accomplish the task. For example, clarify if it will involve 100% **unit tests** and **integration tests** or a different combination.

## Sub-task 2: Perform different types of testing

1. **Unit tests**: Use JUnit or Spock and select a module that needs testing.
2. **Integration tests**: Use JUnit or Spock and cover integration layers.
3. **Component tests**: Cover component scenarios at a business level, specifying exact scenarios and expected outcomes in natural language, preferably using the Cucumber framework.
4. **Contract tests**: Cover all contracts used in specific scenarios, preferably using [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract) or Pact. Contract tests should cover both communication styles: synchronous HTTP and messaging, including stubs propagation.
5. **End-to-end tests**: Describe all scenarios in natural language, focusing on API layer coverage. The Cucumber testing framework can be used along with the component tests mentioned above.

> Note: At least one test should be created and executed for each test type.
