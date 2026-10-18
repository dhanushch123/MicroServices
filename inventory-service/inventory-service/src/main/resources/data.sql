INSERT INTO products (id, name, price, stock) VALUES
(1, 'Smartphone', 699.99, 50),
(2, 'Laptop', 1299.99, 30),
(3, 'Tablet', 499.99, 40),
(4, 'Smartwatch', 199.99, 70),
(5, 'Wireless Headphones', 149.99, 100),
(6, 'Bluetooth Speaker', 89.99, 80),
(7, 'Smart TV', 799.99, 25),
(8, 'Gaming Console', 399.99, 45),
(9, 'External Hard Drive', 129.99, 60),
(10, 'Digital Camera', 599.99, 35),
(11, 'Wireless Mouse', 29.99, 90),
(12, 'Keyboard', 49.99, 85),
(13, 'Monitor', 299.99, 50),
(14, 'Printer', 199.99, 40),
(15, 'Router', 89.99, 70),
(16, 'Smart Home Hub', 149.99, 65),
(17, 'VR Headset', 399.99, 20),
(18, 'Fitness Tracker', 99.99, 75),
(19, 'Drone', 999.99, 30),
(20, 'Portable Charger', 39.99, 100)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('products', 'id'),
    COALESCE((SELECT MAX(id) FROM products), 1)
);