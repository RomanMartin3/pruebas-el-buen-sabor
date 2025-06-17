package com.powerRanger.ElBuenSabor.services;

import com.powerRanger.ElBuenSabor.dtos.*;
import com.powerRanger.ElBuenSabor.entities.*;
import com.powerRanger.ElBuenSabor.entities.enums.Estado;
import com.powerRanger.ElBuenSabor.entities.enums.FormaPago;
import com.powerRanger.ElBuenSabor.entities.enums.Rol;
import com.powerRanger.ElBuenSabor.entities.enums.TipoEnvio;
import com.powerRanger.ElBuenSabor.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;

// Importaciones añadidas/verificadas
import com.powerRanger.ElBuenSabor.dtos.MercadoPagoCreatePreferenceDTO;
import com.powerRanger.ElBuenSabor.entities.Usuario;


@Service
@Validated
public class PedidoServiceImpl implements PedidoService {

    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private DomicilioRepository domicilioRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private ArticuloRepository articuloRepository;
    @Autowired private UsuarioRepository usuarioRepository; // Asegúrate de que esté inyectado
    @Autowired private CarritoRepository carritoRepository;
    @Autowired private CarritoService carritoService;
    @Autowired private ArticuloManufacturadoRepository articuloManufacturadoRepository;
    @Autowired private ArticuloInsumoRepository articuloInsumoRepository;
    @Autowired private LocalidadRepository localidadRepository;
    @Autowired private MercadoPagoService mercadoPagoService; // Asegúrate de que esté inyectado
    @Autowired private SimpMessagingTemplate messagingTemplate;
    private static final Logger logger = LoggerFactory.getLogger(PedidoServiceImpl.class);

    // --- MAPPERS (Como los tenías) ---
    private ArticuloSimpleResponseDTO convertArticuloToSimpleDto(Articulo articulo) {
        if (articulo == null) return null;
        ArticuloSimpleResponseDTO dto = new ArticuloSimpleResponseDTO();
        dto.setId(articulo.getId());
        dto.setDenominacion(articulo.getDenominacion());
        dto.setPrecioVenta(articulo.getPrecioVenta());
        return dto;
    }

    private DetallePedidoResponseDTO convertDetallePedidoToDto(DetallePedido detalle) {
        if (detalle == null) return null;
        DetallePedidoResponseDTO dto = new DetallePedidoResponseDTO();
        dto.setId(detalle.getId());
        dto.setCantidad(detalle.getCantidad());
        dto.setSubTotal(detalle.getSubTotal());
        dto.setArticulo(convertArticuloToSimpleDto(detalle.getArticulo()));
        return dto;
    }
    private PaisResponseDTO convertPaisToDto(Pais pais) {
        if (pais == null) return null;
        PaisResponseDTO dto = new PaisResponseDTO();
        dto.setId(pais.getId());
        dto.setNombre(pais.getNombre());
        return dto;
    }

    private ProvinciaResponseDTO convertProvinciaToDto(Provincia provincia) {
        if (provincia == null) return null;
        ProvinciaResponseDTO dto = new ProvinciaResponseDTO();
        dto.setId(provincia.getId());
        dto.setNombre(provincia.getNombre());
        if (provincia.getPais() != null) {
            dto.setPais(convertPaisToDto(provincia.getPais()));
        }
        return dto;
    }

    private LocalidadResponseDTO convertLocalidadToDto(Localidad localidad) {
        if (localidad == null) return null;
        LocalidadResponseDTO dto = new LocalidadResponseDTO();
        dto.setId(localidad.getId());
        dto.setNombre(localidad.getNombre());
        if (localidad.getProvincia() != null) {
            dto.setProvincia(convertProvinciaToDto(localidad.getProvincia()));
        }
        return dto;
    }
    private DomicilioResponseDTO convertDomicilioToDto(Domicilio domicilio) {
        if (domicilio == null) return null;
        DomicilioResponseDTO dto = new DomicilioResponseDTO();
        dto.setId(domicilio.getId());
        dto.setCalle(domicilio.getCalle());
        dto.setNumero(domicilio.getNumero());
        dto.setCp(domicilio.getCp());
        if (domicilio.getLocalidad() != null) {
            dto.setLocalidad(convertLocalidadToDto(domicilio.getLocalidad()));
        }
        return dto;
    }

    private SucursalResponseDTO convertSucursalToDto(Sucursal sucursal) {
        if (sucursal == null) return null;
        SucursalResponseDTO dto = new SucursalResponseDTO();
        dto.setId(sucursal.getId());
        dto.setNombre(sucursal.getNombre());
        return dto;
    }

    private ClienteResponseDTO convertClienteToDto(Cliente cliente) {
        if (cliente == null) return null;
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNombre(cliente.getNombre());
        dto.setApellido(cliente.getApellido());
        dto.setTelefono(cliente.getTelefono());
        dto.setEmail(cliente.getEmail());
        dto.setFechaNacimiento(cliente.getFechaNacimiento());
        dto.setEstadoActivo(cliente.getEstadoActivo());
        dto.setFechaBaja(cliente.getFechaBaja());
        if (cliente.getUsuario() != null) {
            dto.setUsuarioId(cliente.getUsuario().getId());
            dto.setUsername(cliente.getUsuario().getUsername());
            dto.setRolUsuario(cliente.getUsuario().getRol());
        }
        if (cliente.getDomicilios() != null && !cliente.getDomicilios().isEmpty()) {
            dto.setDomicilios(cliente.getDomicilios().stream().map(this::convertDomicilioToDto).collect(Collectors.toList()));
        } else {
            dto.setDomicilios(new ArrayList<>());
        }
        return dto;
    }

    private PedidoResponseDTO convertToResponseDto(Pedido pedido) {
        if (pedido == null) return null;
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setId(pedido.getId());
        dto.setHoraEstimadaFinalizacion(pedido.getHoraEstimadaFinalizacion());
        dto.setTotal(pedido.getTotal());
        dto.setTotalCosto(pedido.getTotalCosto());
        dto.setFechaPedido(pedido.getFechaPedido());
        dto.setEstado(pedido.getEstado());
        dto.setTipoEnvio(pedido.getTipoEnvio());
        dto.setFormaPago(pedido.getFormaPago());
        dto.setEstadoActivo(pedido.getEstadoActivo());
        dto.setFechaBaja(pedido.getFechaBaja());
        // Incluir el nuevo campo de descuento
        dto.setDescuentoAplicado(pedido.getDescuentoAplicado());
        // Incluir el campo de Mercado Pago preference ID
        dto.setMpPreferenceId(pedido.getMpPreferenceId());


        if (pedido.getSucursal() != null) {
            dto.setSucursal(convertSucursalToDto(pedido.getSucursal()));
        }
        if (pedido.getDomicilio() != null) {
            dto.setDomicilio(convertDomicilioToDto(pedido.getDomicilio()));
        }
        if (pedido.getCliente() != null) {
            dto.setCliente(convertClienteToDto(pedido.getCliente()));
        }
        if (pedido.getDetalles() != null) {
            dto.setDetalles(pedido.getDetalles().stream()
                    .map(this::convertDetallePedidoToDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private LocalTime parseTime(String timeString, String fieldName) throws Exception {
        if (timeString == null || timeString.trim().isEmpty()) {
            throw new Exception("El " + fieldName + " no puede estar vacío.");
        }
        try {
            return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e2){
                throw new Exception("Formato de " + fieldName + " inválido. Use HH:mm:ss o HH:mm. Valor recibido: " + timeString);
            }
        }
    }

    private Pedido mapAndPreparePedido(PedidoRequestDTO dto, Cliente cliente) throws Exception {
        Pedido pedido = new Pedido();
        pedido.setFechaPedido(LocalDate.now());
        pedido.setHoraEstimadaFinalizacion(parseTime(dto.getHoraEstimadaFinalizacion(), "hora estimada de finalización"));
        pedido.setTipoEnvio(dto.getTipoEnvio());
        pedido.setFormaPago(dto.getFormaPago());
        pedido.setEstado(Estado.PENDIENTE);
        pedido.setEstadoActivo(true);
        pedido.setCliente(cliente);

        Domicilio domicilio = domicilioRepository.findById(dto.getDomicilioId())
                .orElseThrow(() -> new Exception("Domicilio no encontrado con ID: " + dto.getDomicilioId()));
        pedido.setDomicilio(domicilio);

        Sucursal sucursal = sucursalRepository.findById(dto.getSucursalId())
                .orElseThrow(() -> new Exception("Sucursal no encontrada con ID: " + dto.getSucursalId()));
        pedido.setSucursal(sucursal);

        double totalPedido = 0.0;
        if (dto.getDetalles() == null || dto.getDetalles().isEmpty()) {
            throw new Exception("El pedido debe contener al menos un detalle.");
        }

        for (DetallePedidoRequestDTO detalleDto : dto.getDetalles()) {
            Articulo articulo = articuloRepository.findById(detalleDto.getArticuloId())
                    .orElseThrow(() -> new Exception("Artículo no encontrado con ID: " + detalleDto.getArticuloId()));
            if (Boolean.FALSE.equals(articulo.getEstadoActivo())) {
                throw new Exception("El artículo '" + articulo.getDenominacion() + "' (ID: " + articulo.getId() + ") no está disponible.");
            }
            DetallePedido detalle = new DetallePedido();
            detalle.setArticulo(articulo);
            detalle.setCantidad(detalleDto.getCantidad());
            if (articulo.getPrecioVenta() == null) {
                throw new Exception("El artículo '" + articulo.getDenominacion() + "' (ID: " + articulo.getId() + ") no tiene un precio de venta asignado.");
            }
            double subTotal = articulo.getPrecioVenta() * detalleDto.getCantidad();
            detalle.setSubTotal(subTotal);
            totalPedido += subTotal;
            pedido.addDetalle(detalle);
        }
        pedido.setTotal(totalPedido);
        return pedido;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> getAll() {
        return pedidoRepository.findAll().stream().map(this::convertToResponseDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoResponseDTO getById(Integer id) throws Exception {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> new Exception("Pedido no encontrado con ID: " + id));
        return convertToResponseDto(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> getPedidosByClienteId(Integer clienteId) throws Exception {
        if (!clienteRepository.existsById(clienteId)) {
            throw new Exception("Cliente no encontrado con ID: " + clienteId);
        }
        return pedidoRepository.findByClienteIdAndEstadoActivoTrueOrderByFechaPedidoDesc(clienteId).stream().map(this::convertToResponseDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> getPedidosByClienteAuth0Id(String auth0Id) throws Exception {
        Usuario usuario = usuarioRepository.findByAuth0Id(auth0Id).orElseThrow(() -> new Exception("Usuario no encontrado con Auth0 ID: " + auth0Id));
        Cliente cliente = clienteRepository.findByUsuarioId(usuario.getId()).orElseThrow(() -> new Exception("Cliente no encontrado para el usuario " + usuario.getUsername()));
        return getPedidosByClienteId(cliente.getId());
    }

    @Override
    @Transactional
    public PedidoResponseDTO create(@Valid PedidoRequestDTO dto) throws Exception {
        Cliente cliente = clienteRepository.findById(dto.getClienteId()).orElseThrow(() -> new Exception("Cliente no encontrado con ID: " + dto.getClienteId()));
        Pedido pedido = mapAndPreparePedido(dto, cliente);
        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        return convertToResponseDto(pedidoGuardado);
    }

    @Override
    @Transactional
    public PedidoResponseDTO createForAuthenticatedClient(String auth0Id, @Valid PedidoRequestDTO dto) throws Exception {
        Usuario usuario = usuarioRepository.findByAuth0Id(auth0Id).orElseThrow(() -> new Exception("Usuario autenticado (Auth0 ID: " + auth0Id + ") no encontrado en el sistema."));
        Cliente cliente = clienteRepository.findByUsuarioId(usuario.getId()).orElseThrow(() -> new Exception("No se encontró un perfil de Cliente para el usuario: " + usuario.getUsername()));
        Pedido pedido = mapAndPreparePedido(dto, cliente);
        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        // <-- AÑADE ESTO -->
        // Notificar la creación del nuevo pedido a los topics.
        System.out.println("WEBSOCKET: Notificando nuevo pedido ID: " + pedidoGuardado.getId());
        messagingTemplate.convertAndSend("/topic/pedidos-cocina", convertToResponseDto(pedidoGuardado));
        messagingTemplate.convertAndSend("/topic/pedidos-cajero", convertToResponseDto(pedidoGuardado));
        // <-- FIN DE LA ADICIÓN -->
        return convertToResponseDto(pedidoGuardado);
    }

    @Override
    @Transactional
    public PedidoResponseDTO crearPedidoDesdeCarrito(Cliente cliente, @Valid CrearPedidoRequestDTO pedidoRequest) throws Exception {
        logger.info("INICIO - crearPedidoDesdeCarrito para Cliente ID: {}", cliente.getId());
        Carrito carrito = carritoRepository.findByCliente(cliente)
                .orElseThrow(() -> new Exception("No se encontró un carrito para el cliente " + cliente.getEmail()));
        if (carrito.getItems() == null || carrito.getItems().isEmpty()) {
            logger.warn("El carrito ID: {} está vacío. Abortando creación de pedido.", carrito.getId());
            throw new Exception("El carrito está vacío. No se puede generar el pedido.");
        }
        logger.info("Carrito ID: {} encontrado con {} items.", carrito.getId(), carrito.getItems().size());

        // --- Lógica de Domicilio: Buscar o Crear ---
        logger.info("Procesando domicilio: Calle {}, N° {}, CP {}", pedidoRequest.getCalleDomicilio(), pedidoRequest.getNumeroDomicilio(), pedidoRequest.getCpDomicilio());
        Domicilio domicilioParaElPedido;
        Localidad localidadDomicilio = localidadRepository.findById(pedidoRequest.getLocalidadIdDomicilio())
                .orElseThrow(() -> new Exception("Localidad no encontrada para el domicilio con ID: " + pedidoRequest.getLocalidadIdDomicilio()));

        Optional<Domicilio> optDomicilioExistente = domicilioRepository.findByCalleAndNumeroAndCpAndLocalidad(
                pedidoRequest.getCalleDomicilio(),
                pedidoRequest.getNumeroDomicilio(),
                pedidoRequest.getCpDomicilio(),
                localidadDomicilio
        );

        if (optDomicilioExistente.isPresent()) {
            domicilioParaElPedido = optDomicilioExistente.get();
            logger.info("Se usará domicilio existente encontrado. ID: {}", domicilioParaElPedido.getId());
        } else {
            Domicilio nuevoDomicilio = new Domicilio();
            nuevoDomicilio.setCalle(pedidoRequest.getCalleDomicilio());
            nuevoDomicilio.setNumero(pedidoRequest.getNumeroDomicilio());
            nuevoDomicilio.setCp(pedidoRequest.getCpDomicilio());
            nuevoDomicilio.setLocalidad(localidadDomicilio);
            domicilioParaElPedido = domicilioRepository.save(nuevoDomicilio);
            logger.info("Se creó un nuevo domicilio. ID: {}", domicilioParaElPedido.getId());
        }

        if (pedidoRequest.getGuardarDireccionEnPerfil() != null && pedidoRequest.getGuardarDireccionEnPerfil()) {
            boolean yaTieneDomicilio = cliente.getDomicilios().stream()
                    .anyMatch(d -> d.getId().equals(domicilioParaElPedido.getId()));
            if (!yaTieneDomicilio) {
                cliente.addDomicilio(domicilioParaElPedido);
                clienteRepository.save(cliente);
                logger.info("Domicilio ID {} asociado al perfil del cliente ID {}", domicilioParaElPedido.getId(), cliente.getId());
            }
        }

        // --- Fin Lógica de Domicilio ---

        Sucursal sucursalPedido = sucursalRepository.findById(pedidoRequest.getSucursalId())
                .orElseThrow(() -> new Exception("Sucursal no encontrada con ID: " + pedidoRequest.getSucursalId()));
        if (sucursalPedido.getEstadoActivo() == null || !sucursalPedido.getEstadoActivo()) {
            logger.warn("Intento de pedido a sucursal inactiva ID: {}", sucursalPedido.getId());
            throw new Exception("La sucursal seleccionada no está activa.");
        }
        logger.info("Sucursal validada. ID: {}", sucursalPedido.getId());

        Map<Integer, Double> insumosAReducirMap = new HashMap<>();
        logger.info("Iniciando Pre-Verificación de Stock...");

        for (CarritoItem item : carrito.getItems()) {
            Articulo articuloBaseDelCarrito = item.getArticulo();
            int cantidadPedida = item.getCantidad();
            logger.info("Verificando stock para CarritoItem ID: {}, Articulo ID: {} ('{}'), Clase Proxy Inicial: {}, Cantidad: {}", item.getId(), articuloBaseDelCarrito.getId(), articuloBaseDelCarrito.getDenominacion(), articuloBaseDelCarrito.getClass().getName(), cantidadPedida);

            Articulo articuloVerificado;
            Optional<ArticuloInsumo> optInsumoStock = articuloInsumoRepository.findById(articuloBaseDelCarrito.getId());
            if (optInsumoStock.isPresent()) {
                articuloVerificado = optInsumoStock.get();
                ArticuloInsumo insumo = (ArticuloInsumo) articuloVerificado;
                logger.info("Es ArticuloInsumo (obtenido de repo para stock): {}, Stock Actual: {}", insumo.getDenominacion(), insumo.getStockActual());
                if (insumo.getEstadoActivo() == null || !insumo.getEstadoActivo()){
                    throw new Exception("El insumo '" + insumo.getDenominacion() + "' ya no está disponible.");
                }
                if (insumo.getStockActual() == null || insumo.getStockActual() < cantidadPedida) {
                    throw new Exception("Stock insuficiente para el insumo: " + insumo.getDenominacion() + ". Solicitado: " + cantidadPedida + ", Disponible: " + (insumo.getStockActual() !=null ? insumo.getStockActual():0) );
                }
                insumosAReducirMap.merge(insumo.getId(), (double) cantidadPedida, Double::sum);
            } else {
                Optional<ArticuloManufacturado> optManufStock = articuloManufacturadoRepository.findById(articuloBaseDelCarrito.getId());
                if (optManufStock.isPresent()) {
                    articuloVerificado = optManufStock.get();
                    ArticuloManufacturado manufacturado = (ArticuloManufacturado) articuloVerificado;
                    logger.info("Es ArticuloManufacturado (obtenido de repo para stock): {}", manufacturado.getDenominacion());
                    if (manufacturado.getEstadoActivo() == null || !manufacturado.getEstadoActivo()){
                        throw new Exception("El artículo manufacturado '" + manufacturado.getDenominacion() + "' ya no está disponible.");
                    }

                    List<ArticuloManufacturadoDetalle> detallesReceta = manufacturado.getManufacturadoDetalles();
                    if (detallesReceta == null || detallesReceta.isEmpty()) {
                        ArticuloManufacturado manufacturadoRecargado = articuloManufacturadoRepository.findById(manufacturado.getId())
                                .orElseThrow(() -> new Exception("No se pudo recargar el manufacturado " + manufacturado.getDenominacion()));
                        detallesReceta = manufacturadoRecargado.getManufacturadoDetalles();
                        if (detallesReceta == null || detallesReceta.isEmpty()) {
                            throw new Exception("El artículo manufacturado '" + manufacturado.getDenominacion() + "' no tiene una receta definida (detalles vacíos o nulos incluso después de recargar).");
                        }
                    }

                    logger.info("Receta para {} tiene {} insumos.", manufacturado.getDenominacion(), detallesReceta.size());
                    for (ArticuloManufacturadoDetalle detalleRecetaItem : detallesReceta) {
                        ArticuloInsumo insumoComponenteOriginal = detalleRecetaItem.getArticuloInsumo();
                        if (insumoComponenteOriginal == null) throw new Exception ("Error en la receta de '"+manufacturado.getDenominacion()+"'.");

                        ArticuloInsumo insumoCompFromDb = articuloInsumoRepository.findById(insumoComponenteOriginal.getId())
                                .orElseThrow(() -> new Exception("Insumo " + insumoComponenteOriginal.getDenominacion() + " de receta no encontrado en DB."));

                        logger.info("--> Insumo de receta: {}, Stock Actual: {}, Cantidad Receta: {}", insumoCompFromDb.getDenominacion(), insumoCompFromDb.getStockActual(), detalleRecetaItem.getCantidad());
                        if (insumoCompFromDb.getEstadoActivo() == null || !insumoCompFromDb.getEstadoActivo()){
                            throw new Exception("El insumo componente '" + insumoCompFromDb.getDenominacion() + "' ya no está disponible.");
                        }
                        double cantidadNecesariaComponenteTotal = detalleRecetaItem.getCantidad() * cantidadPedida;
                        if (insumoCompFromDb.getStockActual() == null || insumoCompFromDb.getStockActual() < cantidadNecesariaComponenteTotal) {
                            throw new Exception("Stock insuficiente del insumo '" + insumoCompFromDb.getDenominacion() + "'. Solicitado: " + cantidadNecesariaComponenteTotal + ", Disponible: " + (insumoCompFromDb.getStockActual() != null ? insumoCompFromDb.getStockActual() : 0) );
                        }
                        insumosAReducirMap.merge(insumoCompFromDb.getId(), cantidadNecesariaComponenteTotal, Double::sum);
                    }
                } else {
                    throw new Exception("Artículo con ID " + articuloBaseDelCarrito.getId() + " ("+articuloBaseDelCarrito.getDenominacion()+") no es ni Insumo ni Manufacturado, o no se encontró en repositorios específicos durante la verificación de stock.");
                }
            }
        }
        logger.info("Pre-Verificación de Stock completada. Insumos a reducir: {}", insumosAReducirMap);

        Pedido nuevoPedido = new Pedido();
        nuevoPedido.setCliente(cliente);
        nuevoPedido.setFechaPedido(LocalDate.now());
        nuevoPedido.setHoraEstimadaFinalizacion(parseTime(pedidoRequest.getHoraEstimadaFinalizacion(), "hora estimada de finalización"));
        nuevoPedido.setDomicilio(domicilioParaElPedido);
        nuevoPedido.setSucursal(sucursalPedido);
        nuevoPedido.setTipoEnvio(pedidoRequest.getTipoEnvio());
        nuevoPedido.setFormaPago(pedidoRequest.getFormaPago());
        nuevoPedido.setEstado(Estado.PENDIENTE);
        nuevoPedido.setEstadoActivo(true);

        double totalGeneralPedido = 0.0;
        double costoTotalPedido = 0.0;
        logger.info("Iniciando cálculo de Total y TotalCosto. costoTotalPedido inicial: {}", costoTotalPedido);

        for (CarritoItem item : carrito.getItems()) {
            DetallePedido detallePedido = new DetallePedido();
            Articulo articuloDelItem;
            Optional<ArticuloInsumo> optInsumo = articuloInsumoRepository.findById(item.getArticulo().getId());
            if (optInsumo.isPresent()) {
                articuloDelItem = optInsumo.get();
            } else {
                Optional<ArticuloManufacturado> optManuf = articuloManufacturadoRepository.findById(item.getArticulo().getId());
                if (optManuf.isPresent()) {
                    articuloDelItem = optManuf.get();
                } else {
                    throw new Exception("Artículo con ID " + item.getArticulo().getId() + " no encontrado en repositorios específicos al crear detalles de pedido.");
                }
            }

            logger.info("Procesando para DetallePedido: Articulo '{}' (ID: {}), Clase Real Obtenida: {}, Cantidad: {}", articuloDelItem.getDenominacion(), articuloDelItem.getId(), articuloDelItem.getClass().getName(), item.getCantidad());
            detallePedido.setArticulo(articuloDelItem);
            detallePedido.setCantidad(item.getCantidad());
            double subTotalItem = item.getCantidad() * item.getPrecioUnitarioAlAgregar();
            detallePedido.setSubTotal(subTotalItem);
            totalGeneralPedido += subTotalItem;

            if (articuloDelItem instanceof ArticuloInsumo) {
                ArticuloInsumo insumo = (ArticuloInsumo) articuloDelItem;
                logger.info("--> Costo Insumo: {}, PrecioCompra: {}, Cantidad: {}", insumo.getDenominacion(), insumo.getPrecioCompra(), item.getCantidad());
                if (insumo.getPrecioCompra() == null) {
                    logger.error("!! ERROR CRITICO: Insumo '{}' (ID: {}) tiene precioCompra NULO.", insumo.getDenominacion(), insumo.getId());
                    throw new Exception("El insumo '"+insumo.getDenominacion()+"' no tiene precio de compra.");
                }
                costoTotalPedido += item.getCantidad() * insumo.getPrecioCompra();
            } else if (articuloDelItem instanceof ArticuloManufacturado) {
                ArticuloManufacturado manufacturado = (ArticuloManufacturado) articuloDelItem;
                logger.info("--> Costo Manufacturado: {}", manufacturado.getDenominacion());
                double costoManufacturadoUnitario = 0.0;

                List<ArticuloManufacturadoDetalle> detallesReceta = manufacturado.getManufacturadoDetalles();
                if (detallesReceta == null || detallesReceta.isEmpty()) {
                    ArticuloManufacturado manufacturadoRecargado = articuloManufacturadoRepository.findById(manufacturado.getId())
                            .orElseThrow(() -> new Exception("No se pudo recargar el manufacturado " + manufacturado.getDenominacion() + " para obtener detalles de receta."));
                    detallesReceta = manufacturadoRecargado.getManufacturadoDetalles();
                    if (detallesReceta == null || detallesReceta.isEmpty()) {
                        throw new Exception("El artículo manufacturado '" + manufacturado.getDenominacion() + "' (ID: " + manufacturado.getId() + ") no tiene detalles de receta para el cálculo de costo (incluso después de recargar).");
                    }
                }
                for (ArticuloManufacturadoDetalle detalleRecetaItem : detallesReceta) {
                    ArticuloInsumo insumoComponenteOriginal = detalleRecetaItem.getArticuloInsumo();
                    if (insumoComponenteOriginal == null) {
                        throw new Exception("Error en receta de '" + manufacturado.getDenominacion() + "': insumo nulo.");
                    }
                    ArticuloInsumo insumoCompConPrecio = articuloInsumoRepository.findById(insumoComponenteOriginal.getId())
                            .orElseThrow(() -> new Exception("Insumo " + insumoComponenteOriginal.getDenominacion() + " de receta no encontrado en BD para costo."));

                    logger.info("----> Receta Insumo: {}, PrecioCompra: {}, Cantidad Receta: {}", insumoCompConPrecio.getDenominacion(), insumoCompConPrecio.getPrecioCompra(), detalleRecetaItem.getCantidad());
                    if (insumoCompConPrecio.getPrecioCompra() == null) {
                        logger.error("!! ERROR CRITICO: Insumo de receta '{}' (ID: {}) tiene precioCompra NULO.", insumoCompConPrecio.getDenominacion(), insumoCompConPrecio.getId());
                        throw new Exception("El insumo componente '"+insumoCompConPrecio.getDenominacion()+"' no tiene precio de compra.");
                    }
                    costoManufacturadoUnitario += detalleRecetaItem.getCantidad() * insumoCompConPrecio.getPrecioCompra();
                }
                logger.info("--> Costo Manufacturado Unitario Calculado: {}", costoManufacturadoUnitario);
                costoTotalPedido += item.getCantidad() * costoManufacturadoUnitario;
            } else {
                logger.warn("!! (Cálculo Costo): Articulo ID {} ({}) no es ni ArticuloInsumo ni ArticuloManufacturado. Clase Real Obtenida: {}", articuloDelItem.getId(), articuloDelItem.getDenominacion(), articuloDelItem.getClass().getName());
            }
            logger.info("--> costoTotalPedido acumulado: {}", costoTotalPedido);
            nuevoPedido.addDetalle(detallePedido);
        }

        double descuento = 0.0;
        if (pedidoRequest.getTipoEnvio() == TipoEnvio.TAKEAWAY && pedidoRequest.getFormaPago() == FormaPago.EFECTIVO) {
            descuento = totalGeneralPedido * 0.10;
            logger.info("Descuento del 10% aplicado para TAKEAWAY/EFECTIVO: -${}", descuento);
            totalGeneralPedido -= descuento;
        }
        nuevoPedido.setDescuentoAplicado(descuento);

        nuevoPedido.setTotal(totalGeneralPedido);
        nuevoPedido.setTotalCosto(costoTotalPedido);
        logger.info("Pedido Final - Total: {}, TotalCosto: {}", nuevoPedido.getTotal(), nuevoPedido.getTotalCosto());

        logger.info("Iniciando Actualización de Stock en la Base de Datos...");
        for (Map.Entry<Integer, Double> entry : insumosAReducirMap.entrySet()) {
            Integer insumoId = entry.getKey();
            Double cantidadADescontar = entry.getValue();
            ArticuloInsumo insumoAActualizar = articuloInsumoRepository.findById(insumoId)
                    .orElseThrow(() -> new Exception("Insumo con ID " + insumoId + " no encontrado para actualizar stock."));

            logger.info("--> Descontando Stock para Insumo ID: {} ('{}'), Cantidad a descontar: {}, Stock actual: {}", insumoId, insumoAActualizar.getDenominacion(), cantidadADescontar, insumoAActualizar.getStockActual());
            if(insumoAActualizar.getStockActual() == null) insumoAActualizar.setStockActual(0.0);
            insumoAActualizar.setStockActual(insumoAActualizar.getStockActual() - cantidadADescontar);
            articuloInsumoRepository.save(insumoAActualizar);
            logger.info("--> Nuevo Stock para Insumo ID {}: {}", insumoId, insumoAActualizar.getStockActual());
        }
        logger.info("Actualización de Stock completada.");

        Pedido pedidoGuardado = pedidoRepository.save(nuevoPedido);
        logger.info("Pedido guardado en DB con ID: {}", pedidoGuardado.getId());

        if (pedidoGuardado.getFormaPago() == FormaPago.MERCADO_PAGO) {
            logger.info("Forma de pago es MERCADO_PAGO. Intentando crear preferencia...");
            try {
                String preferenceId = mercadoPagoService.crearPreferenciaPago(pedidoGuardado);
                pedidoGuardado.setMpPreferenceId(preferenceId);
                pedidoGuardado = pedidoRepository.save(pedidoGuardado);
                logger.info("Preferencia de Mercado Pago creada exitosamente. Pedido actualizado en DB con Preference ID: {}", preferenceId);
            } catch (Exception e) {
                logger.error("!! FALLÓ la creación de la preferencia de Mercado Pago para Pedido ID: {}. Causa: {}", pedidoGuardado.getId(), e.getMessage(), e);
                throw new Exception("No se pudo generar la preferencia de pago. Por favor, intente de nuevo.", e);
            }
        }

        carritoService.vaciarCarrito(cliente);
        logger.info("Carrito vaciado para cliente ID: {}", cliente.getId());
        // <<< INICIO DEL CÓDIGO A AÑADIR >>>
        // Notificar la creación del nuevo pedido a los topics de Cajero y Cocina.
        logger.info("WEBSOCKET: Notificando nuevo pedido ID: {}", pedidoGuardado.getId());
        messagingTemplate.convertAndSend("/topic/pedidos-cajero", convertToResponseDto(pedidoGuardado));
        messagingTemplate.convertAndSend("/topic/pedidos-cocina", convertToResponseDto(pedidoGuardado));
        // <<< FIN DEL CÓDIGO A AÑADIR >>>
        logger.info("FIN - crearPedidoDesdeCarrito ejecutado exitosamente para Pedido ID: {}", pedidoGuardado.getId());

        return convertToResponseDto(pedidoGuardado);
    }

    @Override // Anotación @Override agregada
    @Transactional
    public String createPreferenceMp(String auth0Id, @Valid MercadoPagoCreatePreferenceDTO dto) throws Exception {
        // Validar que el usuario autenticado tiene el rol CLIENTE o es ADMIN
        Usuario usuario = usuarioRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new Exception("Usuario no encontrado con Auth0 ID: " + auth0Id));

        if (!(usuario.getRol() == Rol.CLIENTE || usuario.getRol() == Rol.ADMIN)) {
            throw new Exception("Acceso denegado: Solo clientes o administradores pueden crear preferencias de pago.");
        }

        Pedido pedido = pedidoRepository.findById(dto.getPedidoId())
                .orElseThrow(() -> new Exception("Pedido con ID " + dto.getPedidoId() + " no encontrado."));

        // Opcional: Validar que el pedido pertenece al cliente que lo solicita, a menos que sea un ADMIN
        if (usuario.getRol() == Rol.CLIENTE && !pedido.getCliente().getUsuario().getAuth0Id().equals(auth0Id)) {
            throw new Exception("Acceso denegado: El pedido solicitado no pertenece a este cliente.");
        }

        if (pedido.getMpPreferenceId() != null && !pedido.getMpPreferenceId().isEmpty()) {
            System.out.println("DEBUG MP: El pedido ID " + pedido.getId() + " ya tiene un Preference ID de MP: " + pedido.getMpPreferenceId());
            return pedido.getMpPreferenceId(); // Si ya existe, retorna el existente
        }

        // Si la forma de pago no es Mercado Pago, lanza un error
        if (pedido.getFormaPago() != FormaPago.MERCADO_PAGO) {
            throw new Exception("El pedido con ID " + pedido.getId() + " no está configurado para pago con Mercado Pago.");
        }

        // Asegurarse de que el pedido no esté ya pagado o cancelado
        if (pedido.getEstado() != Estado.PENDIENTE) { // Considera si hay otros estados válidos
            throw new Exception("El pedido con ID " + pedido.getId() + " no está en un estado válido para generar pago. Estado actual: " + pedido.getEstado());
        }

        try {
            String preferenceId = mercadoPagoService.crearPreferenciaPago(pedido);
            pedido.setMpPreferenceId(preferenceId);
            pedidoRepository.save(pedido); // Guarda el preferenceId en el pedido
            return preferenceId;
        } catch (Exception e) {
            System.err.println("ERROR: Falló la creación de la preferencia de Mercado Pago para pedido " + pedido.getId() + ": " + e.getMessage());
            throw new Exception("Error al generar preferencia de Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PedidoResponseDTO updateEstado(Integer id, Estado nuevoEstado) throws Exception {
        Pedido pedidoExistente = pedidoRepository.findById(id).orElseThrow(() -> new Exception("Pedido no encontrado con ID: " + id));
        if (pedidoExistente.getEstado() == Estado.ENTREGADO) {
            if (nuevoEstado == Estado.CANCELADO) throw new Exception("No se puede cancelar un pedido que ya fue entregado.");
            if (nuevoEstado != Estado.ENTREGADO) throw new Exception("Un pedido entregado no puede cambiar a estado: " + nuevoEstado);
        }
        if (pedidoExistente.getEstado() == Estado.CANCELADO && nuevoEstado != Estado.CANCELADO) {
            throw new Exception("No se puede cambiar el estado de un pedido cancelado.");
        }
        if (pedidoExistente.getEstado() == Estado.RECHAZADO && nuevoEstado != Estado.RECHAZADO) {
            throw new Exception("No se puede cambiar el estado de un pedido rechazado.");
        }
        if (pedidoExistente.getEstado() == Estado.PENDIENTE && (nuevoEstado == Estado.ENTREGADO )) { //  Quite EN_CAMINO para simplificar
            throw new Exception("Un pedido pendiente debe pasar por preparación/listo antes de ser entregado.");
        }
        pedidoExistente.setEstado(nuevoEstado);
        Pedido pedidoActualizado = pedidoRepository.save(pedidoExistente);
        // <-- AÑADE ESTO -->
        // Notificar el cambio de estado del pedido.
        System.out.println("WEBSOCKET: Notificando cambio de estado del pedido ID: " + pedidoActualizado.getId() + " a " + nuevoEstado);
        messagingTemplate.convertAndSend("/topic/pedidos-cocina", convertToResponseDto(pedidoActualizado));
        messagingTemplate.convertAndSend("/topic/pedidos-cajero", convertToResponseDto(pedidoActualizado));
        // <-- FIN DE LA ADICIÓN -->
        return convertToResponseDto(pedidoActualizado);
    }

    @Override
    @Transactional
    public void softDelete(Integer id) throws Exception {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> new Exception("Pedido no encontrado con ID: " + id));
        if (pedido.getEstado() == Estado.ENTREGADO) {
            throw new Exception("No se puede eliminar (borrado lógico) un pedido que ya fue entregado.");
        }
        pedido.setEstadoActivo(false);
        pedido.setFechaBaja(LocalDate.now());
        if (pedido.getEstado() != Estado.CANCELADO && pedido.getEstado() != Estado.RECHAZADO && pedido.getEstado() != Estado.ENTREGADO) {
            pedido.setEstado(Estado.CANCELADO);
        }
        Pedido pedidoActualizado = pedidoRepository.save(pedido);
        
        // <-- AÑADE ESTO (VERSIÓN CORREGIDA) -->
        // Notificar la cancelación (borrado lógico) del pedido.
        System.out.println("WEBSOCKET: Notificando cancelación del pedido ID: " + pedidoActualizado.getId() + " a " + pedidoActualizado.getEstado());
        messagingTemplate.convertAndSend("/topic/pedidos-cocina", convertToResponseDto(pedidoActualizado));
        messagingTemplate.convertAndSend("/topic/pedidos-cajero", convertToResponseDto(pedidoActualizado));
        // <-- FIN DE LA ADICIÓN -->
    }
    
    
}