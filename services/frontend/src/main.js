// AI Support Desk - Frontend Principal
// Este archivo maneja la lógica del frontend

import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';
import * as bootstrap from 'bootstrap';

// URL del backend
const API_URL = 'http://localhost:3000';

// Función para hacer peticiones a la API
async function apiCall(endpoint, method = 'GET', body = null) {
  const token = localStorage.getItem('token');
  
  const options = {
    method,
    headers: {
      'Content-Type': 'application/json',
    }
  };

  if (token) {
    options.headers['Authorization'] = `Bearer ${token}`;
  }

  if (body) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${API_URL}${endpoint}`, options);
  return await response.json();
}

// Función para mostrar mensajes
function showMessage(text, type = 'success') {
  const alertDiv = document.createElement('div');
  alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
  alertDiv.innerHTML = `
    ${text}
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  `;
  
  const container = document.querySelector('.container');
  if (container) {
    container.insertBefore(alertDiv, container.firstChild);
    setTimeout(() => alertDiv.remove(), 5000);
  }
}

// Función para redirigir si no hay token
function checkAuth() {
  const token = localStorage.getItem('token');
  const publicPages = ['index.html', 'registro.html', ''];
  const currentPage = window.location.pathname.split('/').pop();
  
  if (!token && !publicPages.includes(currentPage)) {
    window.location.href = 'index.html';
  }
}

// Función para mostrar el nombre del usuario
function loadUserName() {
  const user = JSON.parse(localStorage.getItem('user'));
  if (!user) return;

  // Mostrar en el navbar
  const userNameNav = document.getElementById('userNameNav');
  if (userNameNav) {
    userNameNav.textContent = user.name;
  }

  // Mostrar en el dashboard
  const userNameDashboard = document.getElementById('userNameDashboard');
  if (userNameDashboard) {
    userNameDashboard.textContent = user.name;
  }
}

// Ejecutar al cargar la página
document.addEventListener('DOMContentLoaded', () => {
  checkAuth();
  loadUserName();
  
  // Configurar formulario de login
  const loginForm = document.getElementById('loginForm');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('email').value;
      const password = document.getElementById('password').value;
      
      try {
        const result = await apiCall('/api/auth/login', 'POST', { email, password });
        if (result.token) {
          localStorage.setItem('token', result.token);
          localStorage.setItem('user', JSON.stringify(result.user));
          window.location.href = 'dashboard.html';
        } else {
          showMessage(result.error || 'Error al iniciar sesión', 'danger');
        }
      } catch (error) {
        showMessage('Error de conexión con el servidor', 'danger');
      }
    });
  }

  // Configurar formulario de registro
  const registerForm = document.getElementById('registerForm');
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = document.getElementById('name').value;
      const email = document.getElementById('email').value;
      const password = document.getElementById('password').value;
      
      try {
        const result = await apiCall('/api/auth/register', 'POST', { name, email, password });
        if (result.token) {
          localStorage.setItem('token', result.token);
          localStorage.setItem('user', JSON.stringify(result.user));
          window.location.href = 'dashboard.html';
        } else {
          showMessage(result.error || 'Error al registrar', 'danger');
        }
      } catch (error) {
        showMessage('Error de conexión con el servidor', 'danger');
      }
    });
  }

  // Botón de cerrar sesión
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = 'index.html';
    });
  }

  // Cargar tickets en la página de tickets
  const ticketsTable = document.getElementById('ticketsTable');
  if (ticketsTable) {
    loadTickets();
  }

  // Formulario de crear ticket
  const ticketForm = document.getElementById('ticketForm');
  if (ticketForm) {
    ticketForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const title = document.getElementById('title').value;
      const description = document.getElementById('description').value;
      const priority = document.getElementById('priority').value;
      
      try {
        const result = await apiCall('/api/tickets', 'POST', { title, description, priority });
        if (result.ticket) {
          showMessage('Ticket creado exitosamente');
          setTimeout(() => window.location.href = 'tickets.html', 1500);
        } else {
          showMessage(result.error || 'Error al crear ticket', 'danger');
        }
      } catch (error) {
        showMessage('Error de conexión con el servidor', 'danger');
      }
    });
  }
});

// Función para cargar tickets
async function loadTickets() {
  try {
    const tickets = await apiCall('/api/tickets');
    const tbody = document.getElementById('ticketsTable');
    
    if (tickets.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center">No hay tickets</td></tr>';
      return;
    }

    tbody.innerHTML = tickets.map(ticket => `
      <tr>
        <td>${ticket.id}</td>
        <td>${ticket.title}</td>
        <td><span class="badge bg-${getStatusColor(ticket.status)}">${ticket.status}</span></td>
        <td><span class="badge bg-${getPriorityColor(ticket.priority)}">${ticket.priority}</span></td>
        <td>
          <button class="btn btn-sm btn-outline-primary" onclick="editTicket(${ticket.id})">Editar</button>
          <button class="btn btn-sm btn-outline-danger" onclick="deleteTicket(${ticket.id})">Eliminar</button>
        </td>
      </tr>
    `).join('');
  } catch (error) {
    showMessage('Error al cargar tickets', 'danger');
  }
}

// Funciones auxiliares para colores
function getStatusColor(status) {
  const colors = {
    'Abierto': 'naranja',
    'En proceso': 'secondary',
    'Cerrado': 'success'
  };
  return colors[status] || 'secondary';
}

function getPriorityColor(priority) {
  const colors = {
    'Baja': 'secondary',
    'Normal': 'naranja',
    'Alta': 'warning',
    'Urgente': 'danger'
  };
  return colors[priority] || 'secondary';
}

// Funciones globales para botones
window.editTicket = async (id) => {
  window.location.href = `editar-ticket.html?id=${id}`;
};

window.deleteTicket = async (id) => {
  if (confirm('¿Estás seguro de eliminar este ticket?')) {
    try {
      await apiCall(`/api/tickets/${id}`, 'DELETE');
      showMessage('Ticket eliminado');
      loadTickets();
    } catch (error) {
      showMessage('Error al eliminar ticket', 'danger');
    }
  }
};