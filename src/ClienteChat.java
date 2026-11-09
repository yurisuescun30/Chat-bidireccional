import java.io.BufferedReader; 
import java.io.IOException; 
import java.io.InputStreamReader; 
import java.io.OutputStreamWriter; 
import java.io.PrintWriter; 
import java.net.Socket; 
import java.nio.charset.StandardCharsets; 
 
/** 
 * Cliente del chat bidireccional (Entrega 1). 
 * Pide IP, puerto y nombre; usa un hilo para recibir y otro para enviar. 
 * Comandos: /lista, /con usuario, chao. 
 */ 
public class ClienteChat { 
 
    public static void main(String[] args) { 
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)); 
 
        try { 
            String ip = preguntar(teclado, "IP del Servidor [127.0.0.1]: ", "127.0.0.1"); 
            int puerto = Integer.parseInt(preguntar(teclado, "Puerto de la conexión [59420]: ", 
                    String.valueOf(ServidorChat.PUERTO_POR_DEFECTO))); 
 
            Socket socket = new Socket(ip, puerto); 
            BufferedReader entrada = new BufferedReader( 
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); 
            PrintWriter salida = new PrintWriter( 
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true); 
 
            String respuesta = entrada.readLine(); 
            mostrar(respuesta); 
            while (true) { 
                String nombre = preguntar(teclado, "Escriba su nombre: ", ""); 
                salida.println(nombre); 
                respuesta = entrada.readLine(); 
                if (respuesta == null) { 
                    System.out.println("El servidor cerró la conexión."); 
                    socket.close(); 
                    return; 
                } 
                mostrar(respuesta); 
                if (respuesta.startsWith("OK")) { 
                    break; 
                } 
            } 
 
            System.out.println("Comandos: /lista  |  /con <usuario>  |  chao (salir)"); 
 
            Thread receptor = new Thread(() -> { 
                try { 
                    String linea; 
                    while ((linea = entrada.readLine()) != null) { 
                        mostrar(linea); 
                        if (linea.startsWith("FIN")) { 
                            break; 
                        } 
                    } 
                } catch (IOException e) { 
                    // el socket se cerró 
                } 
                System.out.println("Conexión cerrada."); 
                System.exit(0); 
            }, "Receptor"); 
            receptor.start(); 
 
            String linea; 
            while ((linea = teclado.readLine()) != null) { 
                salida.println(linea); 
                if (linea.trim().equalsIgnoreCase("chao")) { 
                    break; 
                } 
            } 
            receptor.join(3000); 
            socket.close(); 
        } catch (NumberFormatException e) { 
            System.out.println("El puerto debe ser un número."); 
        } catch (IOException e) { 
            System.out.println("No fue posible conectarse al servidor: " + e.getMessage()); 
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        } 
    } 
 
    private static String preguntar(BufferedReader teclado, String texto, String porDefecto) throws IOException { 
        System.out.print(texto); 
        System.out.flush(); 
        String valor = teclado.readLine(); 
        if (valor == null) { 
            throw new IOException("Entrada cerrada"); 
        } 
        valor = valor.trim(); 
        return valor.isEmpty() ? porDefecto : valor; 
    } 
 
    private static synchronized void mostrar(String linea) { 
        if (linea == null) { 
            return; 
        } 
        int espacio = linea.indexOf(' '); 
        String tipo = espacio > 0 ? linea.substring(0, espacio) : linea; 
        String texto = espacio > 0 ? linea.substring(espacio + 1) : ""; 
        switch (tipo) { 
            case "MSG": 
                System.out.println(texto); 
                break; 
            case "ERROR": 
                System.out.println("[!] " + texto); 
                break; 
            default: 
                System.out.println("[" + tipo + "] " + texto); 
        } 
    } 
} 