public class Probe {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("net.minecraft.client.input.KeyEvent");
    for (var m : c.getDeclaredMethods()) System.out.println(m.toString());
    for (var ctor : c.getDeclaredConstructors()) System.out.println(ctor.toString());
  }
}
