import java.util.Scanner;

/** Keeps cancellation behavior consistent across every interactive prompt. */
final class ConsoleInput {
  private ConsoleInput() {
  }

  static String read(Scanner scan) {
    String input = scan.nextLine().trim();
    if (input.equalsIgnoreCase("exit")) {
      throw new CommandCancelledException();
    }
    return input;
  }
}
