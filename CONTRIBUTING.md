# Contributing

Thanks for your interest in contributing to the Weather Forecast app. Please follow these guidelines.

1. Fork the repository and create a feature branch from `main`.
   ```bash
   git checkout -b feature/your-feature
   ```

2. Keep changes small and focused. Write tests for new logic where appropriate.

3. Run linters and build locally before opening a pull request:
   ```bash
   ./gradlew build
   ./gradlew test
   ```

4. Push your branch and open a PR describing the changes and rationale.

5. CI will run unit tests; maintainers will review and request changes if necessary.

6. For larger changes (library upgrades, dependency changes), include a short migration note in the PR and update `CHANGELOG.md`.

7. If you introduce native code or change the build, update `TROUBLESHOOTING.md` with any platform-specific steps.

Thank you — contributions are welcome!
