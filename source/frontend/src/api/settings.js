import request from '../utils/request'

export const getFactions = () => request.get('/settings/factions')
