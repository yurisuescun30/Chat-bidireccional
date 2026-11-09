import java.io.BufferedReader; 
import java.io.IOException; 
import java.io.InputStreamReader; 
import java.io.PrintWriter; 
import java.net.ServerSocket; 
import java.net.Socket; 
import java.nio.charset.StandardCharsets; 
import java.time.LocalTime; 
import java.time.format.DateTimeFormatter; 
import java.util.Map; 
import java.util.concurrent.ConcurrentHashMap; 
 
/** 
 * Servidor del chat bidireccional (Entrega 1). 
 * Abre el puerto 59420, atiende cada cliente en un hilo, registra usuarios, 
 * reenvía mensajes y marca el abandono cuando un usuario envía "chao". 
 * Uso: java chat.ServidorChat [puerto] 
 */ 
public class ServidorChat { 
 
    public static final int PUERTO_POR_DEFECTO = 59420; 
 
    /** Usuarios conectados: nombre -> manejador. */ 
    private static final Map<String, ManejadorCliente> USUARIOS = new ConcurrentHashMap<>(); 
 
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss"); 
 
    public static void main(String[] args) { 
        int puerto = PUERTO_POR_DEFECTO; 
        if (args.length > 0) { 
            puerto = Integer.parseInt(args[0]); 
        } 
        try (ServerSocket servidor = new ServerSocket(puerto)) { 
            log("========== Log del Servidor =========="); 
            log("Servidor iniciado y contestando OK en el puerto " + puerto); 
            while (true) { 
                Socket socketCliente = servidor.accept(); 
                new Thread(new ManejadorCliente(socketCliente)).start(); 
            } 
        } catch (IOException e) { 
            log("Error en el servidor: " + e.getMessage()); 
        } 
    } 
 
    static synchronized void log(String texto) { 
        System.out.println("[" + LocalTime.now().format(HORA) + "] " + texto); 
    } 
 
    static String listaUsuarios() { 
        return USUARIOS.isEmpty() ? "(ninguno)" : String.join(", ", USUARIOS.keySet()); 
    } 
 
    /** Atiende a un único cliente en su propio hilo. */ 
    static class ManejadorCliente implements Runnable { 
 
        private final Socket socket; 
        private BufferedReader entrada; 
        private PrintWriter salida; 
        private String nombre; 
        private String destinatario; 
 
        ManejadorCliente(Socket socket) { 
            this.socket = socket; 
        } 
 
        @Override 
        public void run() { 
            try { 
                entrada = new BufferedReader( 
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); 
                salida = new PrintWriter( 
                        new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true); 
 
                if (!registrar()) { 
                    return; 
                } 
 
                String linea; 
                while ((linea = entrada.readLine()) != null) { 
                    linea = linea.trim(); 
                    if (linea.isEmpty()) { 
                        continue; 
                    } 
                    if (linea.equalsIgnoreCase("chao")) { 
                        despedirse(); 
                        break; 
                    } else if (linea.equalsIgnoreCase("/lista")) { 
                        enviar("LISTA Usuarios conectados: " + listaUsuarios()); 
                    } else if (linea.toLowerCase().startsWith("/con ")) { 
                        elegirDestinatario(linea.substring(5).trim()); 
                    } else { 
                        reenviarMensaje(linea); 
                    } 
                } 
            } catch (IOException e) { 
                // El cliente cerró la ventana o se cayó la red 
            } finally { 
                if (nombre != null && USUARIOS.remove(nombre) != null) { 
                    log("El usuario \"" + nombre + "\" abandonó"); 
                } 
                try { 
                    socket.close(); 
                } catch (IOException ignorada) { 
                    // nada que hacer 
                } 
            } 
        } 
 
        private boolean registrar() throws IOException { 
            enviar("SERVIDOR Conectado. Escriba su nombre de usuario:"); 
            while (true) { 
                String propuesto = entrada.readLine(); 
                if (propuesto == null) { 
                    return false; 
                } 
                propuesto = propuesto.trim(); 
                if (propuesto.isEmpty() || propuesto.contains(" ")) { 
                    enviar("ERROR El nombre no puede estar vacío ni tener espacios. Intente de nuevo:"); 
                } else if (USUARIOS.putIfAbsent(propuesto, this) != null) { 
                    enviar("ERROR El usuario \"" + propuesto + "\" ya está conectado. Use otro nombre:"); 
                } else { 
                    nombre = propuesto; 
                    log("Usuario \"" + nombre + "\" conectado desde " 
                            + socket.getInetAddress().getHostAddress() + ":" + socket.getPort()); 
                    enviar("OK Bienvenido " + nombre); 
                    enviar("LISTA Usuarios conectados: " + listaUsuarios()); 
                    return true; 
                } 
            } 
        } 
 
        private void elegirDestinatario(String otro) { 
            if (otro.equals(nombre)) { 
                enviar("ERROR No puede conversar consigo mismo."); 
            } else if (!USUARIOS.containsKey(otro)) { 
                enviar("ERROR El usuario \"" + otro + "\" no está conectado. Conectados: " + listaUsuarios()); 
            } else { 
                destinatario = otro; 
                enviar("OK Ahora conversa con " + otro + ". Escriba sus mensajes (\"chao\" para salir)."); 
                log("\"" + nombre + "\" eligió conversar con \"" + otro + "\""); 
            } 
        } 
 
        private void reenviarMensaje(String texto) { 
            if (destinatario == null) { 
                enviar("ERROR Primero elija con quién conversar: /con <usuario>"); 
                return; 
            } 
            ManejadorCliente otro = USUARIOS.get(destinatario); 
            if (otro == null) { 
                enviar("ERROR \"" + destinatario + "\" ya abandonó el chat. Elija otro: /con <usuario>"); 
                destinatario = null; 
                return; 
            } 
            String formateado = nombre + "--> " + texto; 
            if (otro.destinatario == null) { 
                otro.destinatario = nombre; 
            } 
            otro.enviar("MSG " + formateado); 
            enviar("MSG " + formateado); 
        } 
 
        private void despedirse() { 
            String formateado = nombre + "--> Chao"; 
            if (destinatario != null) { 
                ManejadorCliente otro = USUARIOS.get(destinatario); 
                if (otro != null) { 
                    otro.enviar("MSG " + formateado); 
                    otro.enviar("SERVIDOR " + nombre + " abandonó el chat."); 
                    otro.destinatario = null; 
                } 
            } 
            enviar("MSG " + formateado); 
            enviar("FIN Sesión terminada. ¡Hasta pronto!"); 
        } 
 
        synchronized void enviar(String texto) { 
            salida.println(texto); 
        } 
    } 
} 